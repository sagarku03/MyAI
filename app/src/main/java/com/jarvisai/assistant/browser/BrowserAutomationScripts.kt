package com.jarvisai.assistant.browser

/**
 * JavaScript payloads injected into WebViews during autonomous AI browser sessions.
 * Locks viewport scaling for deterministic desktop-class coordinate geometry and
 * suppresses Android soft keyboard popups (`inputmode="none"`) while an AI agent is typing.
 */
object BrowserAutomationScripts {

    /**
     * Generates a self-contained JavaScript snippet that toggles the automation input & viewport lock.
     *
     * When [enabled] is `true`:
     * - Locks `<meta name="viewport">` to a deterministic `width=1280, user-scalable=no` layout
     *   (saving the original viewport content in `data-ag-orig-viewport`).
     * - Attaches a `focusin` capture listener and a `MutationObserver` that forces `inputmode="none"`
     *   on `<input>`, `<textarea>`, and `[contenteditable]` nodes so the Android IME does not pop up
     *   and resize the viewport while the agent is dispatching clicks or keystrokes.
     *
     * When [enabled] is `false`:
     * - Disconnects the `MutationObserver` and `focusin` listener.
     * - Restores every element's original `inputmode` attribute and original `<meta name="viewport">`.
     */
    fun inputLock(enabled: Boolean): String {
        return """
            (function() {
                try {
                    var STATE_KEY = '__antigravityInputLockState';
                    var ATTR_ORIG_INPUTMODE = 'data-ag-orig-inputmode';
                    var ATTR_HAD_INPUTMODE = 'data-ag-had-inputmode';
                    var ATTR_ORIG_VIEWPORT = 'data-ag-orig-viewport';
                    var ATTR_CREATED_VIEWPORT = 'data-ag-created-viewport';

                    function lockElementInputMode(el) {
                        if (!el || !el.tagName) return;
                        var tag = el.tagName.toLowerCase();
                        var isEditable = (tag === 'input' || tag === 'textarea' || el.isContentEditable);
                        if (!isEditable) return;
                        if (!el.hasAttribute(ATTR_HAD_INPUTMODE)) {
                            if (el.hasAttribute('inputmode')) {
                                el.setAttribute(ATTR_HAD_INPUTMODE, 'true');
                                el.setAttribute(ATTR_ORIG_INPUTMODE, el.getAttribute('inputmode') || '');
                            } else {
                                el.setAttribute(ATTR_HAD_INPUTMODE, 'false');
                            }
                        }
                        if (el.getAttribute('inputmode') !== 'none') {
                            el.setAttribute('inputmode', 'none');
                        }
                    }

                    function unlockAllElements() {
                        var lockedNodes = document.querySelectorAll('[' + ATTR_HAD_INPUTMODE + ']');
                        for (var i = 0; i < lockedNodes.length; i++) {
                            var node = lockedNodes[i];
                            var had = node.getAttribute(ATTR_HAD_INPUTMODE) === 'true';
                            if (had) {
                                node.setAttribute('inputmode', node.getAttribute(ATTR_ORIG_INPUTMODE) || '');
                            } else {
                                node.removeAttribute('inputmode');
                            }
                            node.removeAttribute(ATTR_HAD_INPUTMODE);
                            node.removeAttribute(ATTR_ORIG_INPUTMODE);
                        }
                    }

                    function lockViewportMeta() {
                        var head = document.head || document.getElementsByTagName('head')[0];
                        if (!head) return;
                        var meta = document.querySelector('meta[name="viewport"]');
                        var targetWidth = Math.max(window.innerWidth || 0, 1280);
                        var lockedContent = 'width=' + targetWidth + ', initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                        if (meta) {
                            if (!meta.hasAttribute(ATTR_ORIG_VIEWPORT)) {
                                meta.setAttribute(ATTR_ORIG_VIEWPORT, meta.getAttribute('content') || '');
                            }
                            meta.setAttribute('content', lockedContent);
                        } else {
                            meta = document.createElement('meta');
                            meta.setAttribute('name', 'viewport');
                            meta.setAttribute(ATTR_CREATED_VIEWPORT, 'true');
                            meta.setAttribute('content', lockedContent);
                            head.appendChild(meta);
                        }
                    }

                    function unlockViewportMeta() {
                        var meta = document.querySelector('meta[name="viewport"]');
                        if (!meta) return;
                        if (meta.getAttribute(ATTR_CREATED_VIEWPORT) === 'true') {
                            if (meta.parentNode) meta.parentNode.removeChild(meta);
                        } else if (meta.hasAttribute(ATTR_ORIG_VIEWPORT)) {
                            meta.setAttribute('content', meta.getAttribute(ATTR_ORIG_VIEWPORT) || '');
                            meta.removeAttribute(ATTR_ORIG_VIEWPORT);
                        }
                    }

                    if ($enabled) {
                        if (window[STATE_KEY] && window[STATE_KEY].active) {
                            return 'already_locked';
                        }
                        lockViewportMeta();
                        var existingInputs = document.querySelectorAll('input, textarea, [contenteditable]');
                        for (var i = 0; i < existingInputs.length; i++) {
                            lockElementInputMode(existingInputs[i]);
                        }
                        var onFocusIn = function(ev) {
                            if (ev && ev.target) {
                                lockElementInputMode(ev.target);
                            }
                        };
                        document.addEventListener('focusin', onFocusIn, true);
                        var observer = new MutationObserver(function(mutations) {
                            for (var m = 0; m < mutations.length; m++) {
                                var added = mutations[m].addedNodes;
                                if (!added) continue;
                                for (var n = 0; n < added.length; n++) {
                                    var item = added[n];
                                    if (item && item.nodeType === 1) {
                                        lockElementInputMode(item);
                                        if (item.querySelectorAll) {
                                            var nested = item.querySelectorAll('input, textarea, [contenteditable]');
                                            for (var k = 0; k < nested.length; k++) {
                                                lockElementInputMode(nested[k]);
                                            }
                                        }
                                    }
                                }
                            }
                        });
                        if (document.documentElement) {
                            observer.observe(document.documentElement, { childList: true, subtree: true });
                        }
                        window[STATE_KEY] = {
                            active: true,
                            onFocusIn: onFocusIn,
                            observer: observer
                        };
                        return 'locked';
                    } else {
                        var st = window[STATE_KEY];
                        if (st) {
                            if (st.onFocusIn) {
                                document.removeEventListener('focusin', st.onFocusIn, true);
                            }
                            if (st.observer) {
                                st.observer.disconnect();
                            }
                            window[STATE_KEY] = null;
                        }
                        unlockAllElements();
                        unlockViewportMeta();
                        return 'unlocked';
                    }
                } catch (err) {
                    return 'error:' + (err && err.message ? err.message : String(err));
                }
            })();
        """.trimIndent()
    }
}
