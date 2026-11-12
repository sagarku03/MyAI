(function() {
    if (window.__antigravityPageAgent) return;

    let nextRefId = 1;
    const refMap = new Map();

    window.__antigravityPageAgent = {
        scanElements: function() {
            refMap.clear();
            const elements = document.querySelectorAll('button, a, input, select, textarea, [role="button"], [clickable]');
            const tree = [];

            elements.forEach(el => {
                const rect = el.getBoundingClientRect();
                if (rect.width > 0 && rect.height > 0 && window.getComputedStyle(el).visibility !== 'hidden') {
                    let ref = el.getAttribute('data-agy-ref');
                    if (!ref) {
                        ref = 'ref_' + (nextRefId++);
                        el.setAttribute('data-agy-ref', ref);
                    }
                    refMap.set(ref, el);

                    tree.push({
                        ref: ref,
                        tag: el.tagName.toLowerCase(),
                        type: el.type || '',
                        text: (el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || el.name || el.id || '').trim().slice(0, 80),
                        bounds: {
                            x: Math.round(rect.left),
                            y: Math.round(rect.top),
                            w: Math.round(rect.width),
                            h: Math.round(rect.height)
                        }
                    });
                }
            });
            return tree;
        },

        findElements: function(description) {
            const all = this.scanElements();
            const query = (description || '').toLowerCase().trim();
            if (!query) return all;
            const tokens = query.split(/\s+/).filter(Boolean);
            return all.filter(item => {
                const haystack = (item.text + ' ' + item.tag + ' ' + item.type + ' ' + item.ref).toLowerCase();
                return tokens.every(t => haystack.includes(t)) || haystack.includes(query);
            });
        },

        getElement: function(ref) {
            return refMap.get(ref) || document.querySelector(`[data-agy-ref="${ref}"]`);
        },

        getElementCenter: function(ref) {
            const el = this.getElement(ref);
            if (!el) return null;
            el.scrollIntoView({ behavior: 'instant', block: 'center', inline: 'center' });
            const rect = el.getBoundingClientRect();
            return {
                x: Math.round(rect.left + rect.width / 2),
                y: Math.round(rect.top + rect.height / 2)
            };
        },

        fillInput: function(ref, value) {
            const el = this.getElement(ref);
            if (!el) return false;
            el.scrollIntoView({ behavior: 'instant', block: 'center' });
            el.focus();
            if (el.tagName.toLowerCase() === 'input' && (el.type === 'checkbox' || el.type === 'radio')) {
                el.checked = value === 'true' || value === '1' || value === 'checked';
            } else {
                const nativeSetter = Object.getOwnPropertyDescriptor(
                    window.HTMLInputElement.prototype,
                    'value'
                )?.set || Object.getOwnPropertyDescriptor(
                    window.HTMLTextAreaElement.prototype,
                    'value'
                )?.set;
                if (nativeSetter) {
                    nativeSetter.call(el, value);
                } else {
                    el.value = value;
                }
            }
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
            return true;
        },

        getPageText: function() {
            return document.body ? document.body.innerText.trim() : '';
        }
    };
})();
