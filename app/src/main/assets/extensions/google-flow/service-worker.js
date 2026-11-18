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
                    chrome.tabs.create({ url: FLOW_URL, active: true }, function(newTab) {
                        resolve(newTab);
                    });
                }
            });
        });
    }

    function injectPromptIntoTab(tabId, promptText) {
        if (!tabId) return;
        chrome.scripting.executeScript({
            target: { tabId: tabId },
            func: function(text) {
                var el = document.querySelector('textarea, input[type="text"], [contenteditable="true"]');
                if (el) {
                    el.focus();
                    if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                        el.value = text;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                    } else {
                        el.innerText = text;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                    var buttons = document.querySelectorAll('button, [role="button"]');
                    for (var i = 0; i < buttons.length; i++) {
                        var label = ((buttons[i].innerText || '') + ' ' + (buttons[i].getAttribute('aria-label') || '')).toLowerCase();
                        if (label.indexOf('generate') !== -1 || label.indexOf('create') !== -1 || label.indexOf('submit') !== -1 || label.indexOf('send') !== -1) {
                            buttons[i].click();
                            return 'SUBMITTED:' + text;
                        }
                    }
                    return 'FILLED:' + text;
                }
                return 'READY_ON_PAGE';
            },
            args: [promptText]
        });
    }

    chrome.runtime.onMessage.addListener(function(message, sender, sendResponse) {
        if (!message || typeof message !== 'object') return false;

        if (message.action === 'START_FLOW_BATCH') {
            var prompts = Array.isArray(message.prompts) ? message.prompts : [];
            var batchId = "flow_batch_" + Date.now();
            ensureFlowTab().then(function(tab) {
                var tid = tab ? tab.id : 1;
                chrome.storage.local.set({
                    lastJobId: batchId,
                    lastBatchCount: prompts.length,
                    lastPrompt: prompts[0] || ""
                });
                if (prompts.length > 0) {
                    injectPromptIntoTab(tid, prompts[0]);
                }
                sendResponse({
                    status: "batch_started",
                    batchId: batchId,
                    count: prompts.length,
                    tabId: tid
                });
            });
            return true;
        }

        if (message.action === 'ping' || message.type === 'ACTION_CLICKED') {
            sendResponse({ status: "online", extension: "google-flow", version: "1.3.0" });
            return false;
        }
        return false;
    });

    window.onMcpToolCall = function(toolName, args) {
        args = args || {};
        if (toolName === "flow_status") {
            return new Promise(function(resolve) {
                chrome.tabs.query({}, function(tabs) {
                    chrome.storage.local.get(["lastJobId", "creditsRemaining"], function(store) {
                        resolve({
                            status: "online",
                            extension: "google-flow",
                            version: "1.3.0",
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
                if (tab && tab.id && imgPrompt) injectPromptIntoTab(tab.id, imgPrompt);
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
                if (tab && tab.id && vidPrompt) injectPromptIntoTab(tab.id, vidPrompt);
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
                if (tab && tab.id && prompts.length > 0) injectPromptIntoTab(tab.id, prompts[0]);
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
                if (tab && tab.id && scenes.length > 0) injectPromptIntoTab(tab.id, scenes[0]);
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
