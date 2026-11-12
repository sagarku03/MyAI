package main

import (
	"bufio"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/gorilla/websocket"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// ============================================================================
// Android-Resilient DNS Resolver (No /etc/resolv.conf dependency)
// ============================================================================

var fallbackDNSServers = []string{
	"8.8.8.8:53",
	"1.1.1.1:53",
	"8.8.4.4:53",
	"9.9.9.9:53",
}

func installAndroidDNSResolver() {
	net.DefaultResolver = &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
			dialer := net.Dialer{
				Timeout:   3 * time.Second,
				KeepAlive: 15 * time.Second,
			}
			// Try requested address first if it is not loopback:53 on stock Android
			if address != "" && !strings.HasPrefix(address, "127.0.0.1:") && !strings.HasPrefix(address, "[::1]:") {
				if conn, err := dialer.DialContext(ctx, network, address); err == nil {
					return conn, nil
				}
			}
			// Cycle through resilient public DNS resolvers over UDP/TCP
			var lastErr error
			for _, dnsTarget := range fallbackDNSServers {
				conn, err := dialer.DialContext(ctx, network, dnsTarget)
				if err == nil {
					return conn, nil
				}
				lastErr = err
			}
			return nil, lastErr
		},
	}
}

// ============================================================================
// Configuration & Process Supervisor Types
// ============================================================================

type BridgeConfig struct {
	BindAddr     string
	Port         int
	HubPort      int
	ClaudePort   int
	CodexPort    int
	OpenCodePort int
	CSRFToken    string
	WorkspaceDir string
	PrefixDir    string
	HomeDir      string
}

type ManagedDaemon struct {
	Name        string    `json:"name"`
	Command     string    `json:"command"`
	Args        []string  `json:"args"`
	Port        int       `json:"port"`
	PID         int       `json:"pid"`
	Status      string    `json:"status"`
	Restarts    int       `json:"restarts"`
	LastStarted time.Time `json:"lastStarted"`
	LastError   string    `json:"lastError,omitempty"`

	mu      sync.Mutex
	cmd     *exec.Cmd
	cancel  context.CancelFunc
	logs    []string
	maxLogs int
}

func (d *ManagedDaemon) appendLog(line string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.maxLogs <= 0 {
		d.maxLogs = 250
	}
	d.logs = append(d.logs, fmt.Sprintf("[%s] %s", time.Now().Format("15:04:05"), line))
	if len(d.logs) > d.maxLogs {
		d.logs = d.logs[len(d.logs)-d.maxLogs:]
	}
}

func (d *ManagedDaemon) snapshotLogs() []string {
	d.mu.Lock()
	defer d.mu.Unlock()
	out := make([]string, len(d.logs))
	copy(out, d.logs)
	return out
}

type Supervisor struct {
	cfg        BridgeConfig
	daemons    map[string]*ManagedDaemon
	mu         sync.RWMutex
	wsClients  map[*websocket.Conn]bool
	wsMu       sync.Mutex
	upgrader   websocket.Upgrader
	grpcConn   *grpc.ClientConn
	shutdownCh chan struct{}
}

type WSEvent struct {
	Type      string      `json:"type"`
	Daemon    string      `json:"daemon,omitempty"`
	Timestamp int64       `json:"timestamp"`
	Payload   interface{} `json:"payload"`
}

func generateCSRFToken(length int) string {
	b := make([]byte, (length+1)/2)
	if _, err := rand.Read(b); err != nil {
		return "jarviscsrf12"
	}
	return hex.EncodeToString(b)[:length]
}

func NewSupervisor(cfg BridgeConfig) *Supervisor {
	s := &Supervisor{
		cfg:        cfg,
		daemons:    make(map[string]*ManagedDaemon),
		wsClients:  make(map[*websocket.Conn]bool),
		shutdownCh: make(chan struct{}),
		upgrader: websocket.Upgrader{
			ReadBufferSize:  16384,
			WriteBufferSize: 16384,
			CheckOrigin: func(r *http.Request) bool {
				return true
			},
		},
	}

	s.registerDefaultDaemons()
	return s
}

func (s *Supervisor) registerDefaultDaemons() {
	s.daemons["agy"] = &ManagedDaemon{
		Name:    "agy",
		Command: s.resolveBinary("agy"),
		Args: []string{
			"--hub",
			fmt.Sprintf("--hub-port=%d", s.cfg.HubPort),
			"--app_data_dir=jarvis-antigravity",
		},
		Port:    s.cfg.HubPort,
		Status:  "stopped",
		maxLogs: 300,
	}

	s.daemons["claude"] = &ManagedDaemon{
		Name:    "claude",
		Command: s.resolveBinary("claude"),
		Args: []string{
			"--output-format", "stream-json",
			"--verbose",
		},
		Port:    s.cfg.ClaudePort,
		Status:  "standby",
		maxLogs: 300,
	}

	s.daemons["codex"] = &ManagedDaemon{
		Name:    "codex",
		Command: s.resolveBinary("codex"),
		Args: []string{
			"--quiet",
		},
		Port:    s.cfg.CodexPort,
		Status:  "standby",
		maxLogs: 300,
	}

	s.daemons["opencode"] = &ManagedDaemon{
		Name:    "opencode",
		Command: s.resolveBinary("opencode"),
		Args: []string{
			"serve",
			"--port", fmt.Sprintf("%d", s.cfg.OpenCodePort),
			"--hostname", "127.0.0.1",
		},
		Port:    s.cfg.OpenCodePort,
		Status:  "stopped",
		maxLogs: 300,
	}
}

func (s *Supervisor) resolveBinary(name string) string {
	candidates := []string{
		filepath.Join(s.cfg.PrefixDir, "bin", name),
		filepath.Join(s.cfg.HomeDir, ".local", "bin", name),
		filepath.Join("/data/data/com.termux/files/usr/bin", name),
	}
	for _, c := range candidates {
		if info, err := os.Stat(c); err == nil && !info.IsDir() {
			return c
		}
	}
	if p, err := exec.LookPath(name); err == nil {
		return p
	}
	return name
}

func (s *Supervisor) buildChildEnv() []string {
	env := os.Environ()
	pathVal := fmt.Sprintf(
		"%s/bin:%s/.local/bin:/data/data/com.termux/files/usr/bin:/system/bin",
		s.cfg.PrefixDir,
		s.cfg.HomeDir,
	)
	overrides := map[string]string{
		"PREFIX":            s.cfg.PrefixDir,
		"HOME":              s.cfg.HomeDir,
		"TMPDIR":            filepath.Join(s.cfg.PrefixDir, "tmp"),
		"PATH":              pathVal,
		"TERM":              "xterm-256color",
		"COLORTERM":         "truecolor",
		"LANG":              "en_US.UTF-8",
		"GODEBUG":           "netdns=go",
		"RES_OPTIONS":       "ndots:1 timeout:2 attempts:2",
		"AGY_BRIDGE_TOKEN":  s.cfg.CSRFToken,
		"JARVIS_HUB_PORT":   fmt.Sprintf("%d", s.cfg.HubPort),
		"JARVIS_BRIDGE_URL": fmt.Sprintf("http://%s:%d", s.cfg.BindAddr, s.cfg.Port),
	}
	for k, v := range overrides {
		env = append(env, fmt.Sprintf("%s=%s", k, v))
	}
	return env
}

func (s *Supervisor) StartDaemon(ctx context.Context, name string) error {
	s.mu.RLock()
	d, ok := s.daemons[name]
	s.mu.RUnlock()
	if !ok {
		return fmt.Errorf("unknown daemon: %s", name)
	}

	d.mu.Lock()
	if d.Status == "running" && d.cmd != nil && d.cmd.Process != nil {
		d.mu.Unlock()
		return nil
	}

	binPath := s.resolveBinary(d.Name)
	if _, err := os.Stat(binPath); err != nil {
		if _, lookErr := exec.LookPath(d.Name); lookErr != nil {
			d.Status = "missing_binary"
			d.LastError = fmt.Sprintf("binary '%s' not installed in %s/bin", d.Name, s.cfg.PrefixDir)
			d.mu.Unlock()
			return fmt.Errorf("%s", d.LastError)
		}
	}

	childCtx, cancel := context.WithCancel(ctx)
	cmd := exec.CommandContext(childCtx, binPath, d.Args...)
	cmd.Dir = s.cfg.WorkspaceDir
	cmd.Env = s.buildChildEnv()

	stdoutPipe, err := cmd.StdoutPipe()
	if err != nil {
		cancel()
		d.mu.Unlock()
		return err
	}
	stderrPipe, err := cmd.StderrPipe()
	if err != nil {
		cancel()
		d.mu.Unlock()
		return err
	}

	if err := cmd.Start(); err != nil {
		cancel()
		d.Status = "error"
		d.LastError = err.Error()
		d.mu.Unlock()
		return err
	}

	d.cmd = cmd
	d.cancel = cancel
	d.PID = cmd.Process.Pid
	d.Status = "running"
	d.LastStarted = time.Now()
	d.LastError = ""
	d.mu.Unlock()

	s.broadcastEvent(WSEvent{
		Type:      "daemon_started",
		Daemon:    d.Name,
		Timestamp: time.Now().UnixMilli(),
		Payload: map[string]interface{}{
			"pid":  d.PID,
			"port": d.Port,
		},
	})

	go s.streamPipe(d, stdoutPipe, "stdout")
	go s.streamPipe(d, stderrPipe, "stderr")

	go func() {
		waitErr := cmd.Wait()
		d.mu.Lock()
		d.PID = 0
		if waitErr != nil {
			d.Status = "exited"
			d.LastError = waitErr.Error()
		} else {
			d.Status = "stopped"
		}
		d.mu.Unlock()

		s.broadcastEvent(WSEvent{
			Type:      "daemon_exited",
			Daemon:    d.Name,
			Timestamp: time.Now().UnixMilli(),
			Payload: map[string]interface{}{
				"status": d.Status,
				"error":  d.LastError,
			},
		})
	}()

	return nil
}

func (s *Supervisor) StopDaemon(name string) error {
	s.mu.RLock()
	d, ok := s.daemons[name]
	s.mu.RUnlock()
	if !ok {
		return fmt.Errorf("unknown daemon: %s", name)
	}

	d.mu.Lock()
	defer d.mu.Unlock()

	if d.cancel != nil {
		d.cancel()
	}
	if d.cmd != nil && d.cmd.Process != nil {
		_ = d.cmd.Process.Signal(syscall.SIGTERM)
		done := make(chan error, 1)
		go func(p *os.Process) {
			_, err := p.Wait()
			done <- err
		}(d.cmd.Process)

		select {
		case <-done:
		case <-time.After(2 * time.Second):
			_ = d.cmd.Process.Kill()
		}
	}
	d.PID = 0
	d.Status = "stopped"
	return nil
}

func (s *Supervisor) StopAll() {
	s.mu.RLock()
	names := make([]string, 0, len(s.daemons))
	for k := range s.daemons {
		names = append(names, k)
	}
	s.mu.RUnlock()

	for _, name := range names {
		_ = s.StopDaemon(name)
	}
	if s.grpcConn != nil {
		_ = s.grpcConn.Close()
	}
}

func (s *Supervisor) streamPipe(d *ManagedDaemon, r io.Reader, streamName string) {
	scanner := bufio.NewScanner(r)
	buf := make([]byte, 0, 64*1024)
	scanner.Buffer(buf, 1024*1024)
	for scanner.Scan() {
		line := scanner.Text()
		d.appendLog(line)
		s.broadcastEvent(WSEvent{
			Type:      "daemon_log",
			Daemon:    d.Name,
			Timestamp: time.Now().UnixMilli(),
			Payload: map[string]string{
				"stream": streamName,
				"line":   line,
			},
		})
	}
}

func (s *Supervisor) broadcastEvent(ev WSEvent) {
	data, err := json.Marshal(ev)
	if err != nil {
		return
	}
	s.wsMu.Lock()
	defer s.wsMu.Unlock()
	for conn := range s.wsClients {
		_ = conn.SetWriteDeadline(time.Now().Add(4 * time.Second))
		if err := conn.WriteMessage(websocket.TextMessage, data); err != nil {
			_ = conn.Close()
			delete(s.wsClients, conn)
		}
	}
}

// ============================================================================
// CSRF Token Authentication Middleware & Reverse Proxy
// ============================================================================

func (s *Supervisor) verifyCSRF(r *http.Request) bool {
	if s.cfg.CSRFToken == "" {
		return true
	}
	headerToken := r.Header.Get("X-CSRF-Token")
	if headerToken == "" {
		headerToken = r.Header.Get("X-Bridge-Token")
	}
	if headerToken == "" {
		auth := r.Header.Get("Authorization")
		if strings.HasPrefix(auth, "Bearer ") {
			headerToken = strings.TrimPrefix(auth, "Bearer ")
		}
	}
	if headerToken == "" {
		headerToken = r.URL.Query().Get("token")
	}
	return subtle.ConstantTimeCompare([]byte(headerToken), []byte(s.cfg.CSRFToken)) == 1
}

func (s *Supervisor) withAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CSRF-Token, X-Bridge-Token")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		if !s.verifyCSRF(r) {
			http.Error(w, `{"error":"invalid or missing CSRF security token"}`, http.StatusUnauthorized)
			return
		}
		next(w, r)
	}
}

func (s *Supervisor) buildReverseProxy(targetPort int, stripPrefix string) http.HandlerFunc {
	targetURL, _ := url.Parse(fmt.Sprintf("http://127.0.0.1:%d", targetPort))
	proxy := httputil.NewSingleHostReverseProxy(targetURL)
	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadGateway)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"error":      "upstream agent daemon unreachable",
			"targetPort": targetPort,
			"detail":     err.Error(),
		})
	}
	return func(w http.ResponseWriter, r *http.Request) {
		r.URL.Path = strings.TrimPrefix(r.URL.Path, stripPrefix)
		if r.URL.Path == "" {
			r.URL.Path = "/"
		}
		proxy.ServeHTTP(w, r)
	}
}

func (s *Supervisor) handleHealth(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	statuses := make(map[string]interface{}, len(s.daemons))
	for name, d := range s.daemons {
		d.mu.Lock()
		statuses[name] = map[string]interface{}{
			"status":    d.Status,
			"pid":       d.PID,
			"port":      d.Port,
			"restarts":  d.Restarts,
			"lastError": d.LastError,
		}
		d.mu.Unlock()
	}
	s.mu.RUnlock()

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":       "ok",
		"service":      "agy_ide_bridge",
		"goVersion":    runtime.Version(),
		"arch":         fmt.Sprintf("%s/%s", runtime.GOOS, runtime.GOARCH),
		"bridgePort":   s.cfg.Port,
		"hubPort":      s.cfg.HubPort,
		"workspaceDir": s.cfg.WorkspaceDir,
		"daemons":      statuses,
		"timestamp":    time.Now().UnixMilli(),
	})
}

func (s *Supervisor) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	if !s.verifyCSRF(r) {
		http.Error(w, "Unauthorized WebSocket handshake", http.StatusUnauthorized)
		return
	}
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}

	s.wsMu.Lock()
	s.wsClients[conn] = true
	s.wsMu.Unlock()

	defer func() {
		s.wsMu.Lock()
		delete(s.wsClients, conn)
		s.wsMu.Unlock()
		_ = conn.Close()
	}()

	// Send initial handshake frame
	_ = conn.WriteJSON(WSEvent{
		Type:      "bridge_connected",
		Timestamp: time.Now().UnixMilli(),
		Payload: map[string]interface{}{
			"workspace": s.cfg.WorkspaceDir,
			"hubPort":   s.cfg.HubPort,
		},
	})

	for {
		_, msgBytes, err := conn.ReadMessage()
		if err != nil {
			break
		}
		var req struct {
			Action  string   `json:"action"`
			Daemon  string   `json:"daemon"`
			Command string   `json:"command"`
			Args    []string `json:"args"`
		}
		if err := json.Unmarshal(msgBytes, &req); err != nil {
			continue
		}

		switch req.Action {
		case "start_daemon":
			err := s.StartDaemon(context.Background(), req.Daemon)
			_ = conn.WriteJSON(WSEvent{
				Type:      "action_result",
				Daemon:    req.Daemon,
				Timestamp: time.Now().UnixMilli(),
				Payload: map[string]interface{}{
					"action": "start_daemon",
					"ok":     err == nil,
					"error":  errString(err),
				},
			})
		case "stop_daemon":
			err := s.StopDaemon(req.Daemon)
			_ = conn.WriteJSON(WSEvent{
				Type:      "action_result",
				Daemon:    req.Daemon,
				Timestamp: time.Now().UnixMilli(),
				Payload: map[string]interface{}{
					"action": "stop_daemon",
					"ok":     err == nil,
					"error":  errString(err),
				},
			})
		case "exec":
			go s.runOneShotCommand(conn, req.Command, req.Args)
		case "logs":
			s.mu.RLock()
			d, ok := s.daemons[req.Daemon]
			s.mu.RUnlock()
			if ok {
				_ = conn.WriteJSON(WSEvent{
					Type:      "daemon_logs_snapshot",
					Daemon:    req.Daemon,
					Timestamp: time.Now().UnixMilli(),
					Payload:   d.snapshotLogs(),
				})
			}
		}
	}
}

func (s *Supervisor) runOneShotCommand(conn *websocket.Conn, command string, args []string) {
	if command == "" {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	cmd := exec.CommandContext(ctx, s.resolveBinary(command), args...)
	cmd.Dir = s.cfg.WorkspaceDir
	cmd.Env = s.buildChildEnv()
	out, err := cmd.CombinedOutput()

	s.wsMu.Lock()
	defer s.wsMu.Unlock()
	_ = conn.WriteJSON(WSEvent{
		Type:      "exec_result",
		Timestamp: time.Now().UnixMilli(),
		Payload: map[string]interface{}{
			"command": command,
			"output":  string(out),
			"error":   errString(err),
		},
	})
}

func (s *Supervisor) connectHubGRPC(ctx context.Context) {
	target := fmt.Sprintf("127.0.0.1:%d", s.cfg.HubPort)
	conn, err := grpc.DialContext(
		ctx,
		target,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err == nil {
		s.grpcConn = conn
	}
}

func errString(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

// ============================================================================
// Entrypoint
// ============================================================================

func main() {
	installAndroidDNSResolver()

	defaultPrefix := os.Getenv("PREFIX")
	if defaultPrefix == "" {
		defaultPrefix = "/data/data/com.termux/files/usr"
	}
	defaultHome := os.Getenv("HOME")
	if defaultHome == "" {
		defaultHome = "/data/data/com.termux/files/home"
	}

	port := flag.Int("port", 1234, "IDE bridge HTTP/WebSocket listen port")
	hubPort := flag.Int("hub-port", 1235, "Antigravity gRPC/HTTP Hub port")
	claudePort := flag.Int("claude-port", 3284, "Claude Code bridge daemon port")
	codexPort := flag.Int("codex-port", 3285, "OpenAI Codex daemon port")
	openCodePort := flag.Int("opencode-port", 4096, "OpenCode HTTP server port")
	bindAddr := flag.String("bind", "127.0.0.1", "Loopback bind address")
	csrfToken := flag.String("token", "", "12-char CSRF security token (auto-generated if empty)")
	workspaceDir := flag.String("dir", defaultHome, "Default coding workspace directory")
	autoStartAgy := flag.Bool("autostart-agy", true, "Automatically launch agy hub daemon on startup")
	flag.Parse()

	token := strings.TrimSpace(*csrfToken)
	if token == "" {
		token = generateCSRFToken(12)
	}

	_ = os.MkdirAll(*workspaceDir, 0o755)

	cfg := BridgeConfig{
		BindAddr:     *bindAddr,
		Port:         *port,
		HubPort:      *hubPort,
		ClaudePort:   *claudePort,
		CodexPort:    *codexPort,
		OpenCodePort: *openCodePort,
		CSRFToken:    token,
		WorkspaceDir: *workspaceDir,
		PrefixDir:    defaultPrefix,
		HomeDir:      defaultHome,
	}

	supervisor := NewSupervisor(cfg)
	rootCtx, rootCancel := context.WithCancel(context.Background())
	defer rootCancel()

	if *autoStartAgy {
		if err := supervisor.StartDaemon(rootCtx, "agy"); err != nil {
			log.Printf("[agy_ide_bridge] Note: agy daemon not started immediately (%v)", err)
		}
		go supervisor.connectHubGRPC(rootCtx)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/", supervisor.handleHealth)
	mux.HandleFunc("/health", supervisor.handleHealth)
	mux.HandleFunc("/ws", supervisor.handleWebSocket)

	// Reverse proxy routes for the 4-Agent subsystem
	mux.HandleFunc("/api/agy/", supervisor.withAuth(supervisor.buildReverseProxy(cfg.HubPort, "/api/agy")))
	mux.HandleFunc("/api/claude/", supervisor.withAuth(supervisor.buildReverseProxy(cfg.ClaudePort, "/api/claude")))
	mux.HandleFunc("/api/codex/", supervisor.withAuth(supervisor.buildReverseProxy(cfg.CodexPort, "/api/codex")))
	mux.HandleFunc("/api/opencode/", supervisor.withAuth(supervisor.buildReverseProxy(cfg.OpenCodePort, "/api/opencode")))

	serverAddr := fmt.Sprintf("%s:%d", cfg.BindAddr, cfg.Port)
	httpServer := &http.Server{
		Addr:              serverAddr,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
	}

	sigCh := make(chan os.Signal, 2)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		sig := <-sigCh
		log.Printf("[agy_ide_bridge] Caught signal %v — shutting down child daemons...", sig)
		rootCancel()
		supervisor.StopAll()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = httpServer.Shutdown(shutdownCtx)
	}()

	log.Printf("[agy_ide_bridge] Listening on http://%s (Hub=%d, Workspace=%s, CSRF=%s)",
		serverAddr, cfg.HubPort, cfg.WorkspaceDir, cfg.CSRFToken)

	if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("[agy_ide_bridge] Fatal server error: %v", err)
	}
}
