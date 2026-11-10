(function() {
    var FLOW_URL = "https://labs.google/fx/tools/flow";

    function ensureFlowTab() {
        return new Promise(function(resolve) {
            chrome.tabs.query({}, function(tabs) {
                var existing = null;
                if (tabs && tabs.length) {
                    for (var i = 0; i < tabs.length; i++) {
                        if (tabs[i].url && tabs[i].url.indexOf("labs.google") !== -1) {
                            existing = tabs[i];
                            break;
                        }
                    }
                }
                if (existing) {
                    resolve(existing);
                } else {
                    chrome.tabs.create({ url: FLOW_URL }, function(newTab) {
                        resolve(newTab);
                    });
                }
            });
        });
    }

    window.onMcpToolCall = function(toolName, args) {
        args = args || {};
        if (toolName === "flow_status") {
            return new Promise(function(resolve) {
                chrome.tabs.query({}, function(tabs) {
                    chrome.storage.local.get(["lastJobId", "creditsRemaining"], function(store) {
                        resolve({
                            status: "online",
                            extension: "google-flow",
                            version: "1.2.0",
                            activeTabs: tabs ? tabs.length : 0,
                            lastJobId: (store && store.lastJobId) ? store.lastJobId : null,
                            ready: true
                        });
                    });
                });
            });
        }

        if (toolName === "flow_generate_image") {
            var imgPrompt = args.prompt || "";
            var aspectRatio = args.aspectRatio || "16:9";
            var imgJobId = "flow_img_" + Date.now();
            return ensureFlowTab().then(function(tab) {
                chrome.storage.local.set({ lastJobId: imgJobId, lastPrompt: imgPrompt });
                return {
                    status: "dispatched",
                    jobId: imgJobId,
                    mode: "image",
                    prompt: imgPrompt,
                    aspectRatio: aspectRatio,
                    tabId: tab ? tab.id : null
                };
            });
        }

        if (toolName === "flow_generate_video") {
            var vidPrompt = args.prompt || "";
            var vidJobId = "flow_vid_" + Date.now();
            return ensureFlowTab().then(function(tab) {
                chrome.storage.local.set({ lastJobId: vidJobId, lastPrompt: vidPrompt });
                return {
                    status: "dispatched",
                    jobId: vidJobId,
                    mode: "video",
                    prompt: vidPrompt,
                    tabId: tab ? tab.id : null
                };
            });
        }

        if (toolName === "deepx_rapid_batch_dispatch") {
            var prompts = Array.isArray(args.prompts) ? args.prompts : [];
            var batchId = "deepx_batch_" + Date.now();
            return ensureFlowTab().then(function(tab) {
                chrome.storage.local.set({ lastJobId: batchId });
                return {
                    status: "batch_queued",
                    batchId: batchId,
                    count: prompts.length,
                    prompts: prompts,
                    tabId: tab ? tab.id : null
                };
            });
        }

        if (toolName === "deepx_rapid_storyboard_autopilot") {
            var scriptText = args.script || "";
            var scenes = scriptText
                .split(/\n+/)
                .map(function(line) { return line.trim(); })
                .filter(function(line) { return line.length > 0; });
            var storyboardId = "deepx_sb_" + Date.now();
            return ensureFlowTab().then(function(tab) {
                chrome.storage.local.set({ lastJobId: storyboardId });
                return {
                    status: "storyboard_started",
                    storyboardId: storyboardId,
                    sceneCount: scenes.length,
                    scenes: scenes,
                    tabId: tab ? tab.id : null
                };
            });
        }

        return Promise.reject("Unsupported Google Flow tool: " + toolName);
    };
})();
