(function(global) {
    "use strict";

    var DEFAULT_HOVER_DELAY_MS = 450;
    var EDGE_PADDING = 8;

    function normalizePosition(pos) {
        pos = pos || {};
        return {
            row: Math.max(0, Number(pos.row) || 0),
            column: Math.max(0, Number(pos.column) || 0)
        };
    }

    function escapeHtml(value) {
        return String(value || "").replace(/[&<>"']/g, function(ch) {
            return {
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                '"': "&quot;",
                "'": "&#39;"
            }[ch];
        });
    }

    function renderInlineMarkdown(text) {
        var html = escapeHtml(text);
        html = html.replace(/`([^`]+)`/g, "<code>$1</code>");
        html = html.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
        html = html.replace(/\*([^*]+)\*/g, "<em>$1</em>");
        return html;
    }

    function renderMarkdown(text) {
        var lines = String(text || "").replace(/\r\n?/g, "\n").split("\n");
        var html = [];
        var paragraph = [];
        var listOpen = false;
        var codeOpen = false;

        function closeParagraph() {
            if (!paragraph.length) {
                return;
            }
            html.push('<p>' + paragraph.join("<br>") + '</p>');
            paragraph = [];
        }

        function closeList() {
            if (!listOpen) {
                return;
            }
            html.push("</ul>");
            listOpen = false;
        }

        lines.forEach(function(line) {
            var fence = /^\s*```/.test(line);
            if (fence) {
                closeParagraph();
                closeList();
                if (codeOpen) {
                    html.push("</code></pre>");
                } else {
                    html.push('<pre><code>');
                }
                codeOpen = !codeOpen;
                return;
            }

            if (codeOpen) {
                html.push(escapeHtml(line) + "\n");
                return;
            }

            if (!line.trim()) {
                closeParagraph();
                closeList();
                return;
            }

            var heading = /^(#{1,3})\s+(.+)$/.exec(line);
            if (heading) {
                closeParagraph();
                closeList();
                html.push(
                    '<div class="autojs6_hover_tooltip_heading autojs6_hover_tooltip_heading_' +
                        heading[1].length + '">' + renderInlineMarkdown(heading[2]) + "</div>"
                );
                return;
            }

            var bullet = /^\s*[-*]\s+(.+)$/.exec(line);
            if (bullet) {
                closeParagraph();
                if (!listOpen) {
                    html.push("<ul>");
                    listOpen = true;
                }
                html.push("<li>" + renderInlineMarkdown(bullet[1]) + "</li>");
                return;
            }

            closeList();
            paragraph.push(renderInlineMarkdown(line));
        });

        closeParagraph();
        closeList();
        if (codeOpen) {
            html.push("</code></pre>");
        }
        return html.join("");
    }

    function isTouchEvent(event) {
        var domEvent = event && event.domEvent || event || {};
        return domEvent.pointerType === "touch" ||
            !!domEvent.touches ||
            !!domEvent.changedTouches;
    }

    function notify(config, message, error) {
        if (config && typeof config.notifyError === "function") {
            config.notifyError(String(message || "ACE tooltip error"), error);
        }
    }

    function createController(config) {
        config = config || {};
        var editor = config.editor || null;
        var session = config.session || (editor && editor.session) || null;
        var hoverDelayMs = Math.max(0, Number(config.hoverDelayMs) || DEFAULT_HOVER_DELAY_MS);
        var element = null;
        var captionElement = null;
        var contentElement = null;
        var hoverTimer = null;
        var attached = false;
        var visible = false;
        var markdownLoaded = false;
        var markdownRenderer = null;

        function ensureElement() {
            if (element) {
                return element;
            }
            element = global.document.createElement("div");
            element.className = "autojs6_hover_tooltip";
            element.style.display = "none";

            captionElement = global.document.createElement("div");
            captionElement.className = "autojs6_hover_tooltip_caption";
            element.appendChild(captionElement);

            contentElement = global.document.createElement("div");
            contentElement.className = "autojs6_hover_tooltip_content";
            element.appendChild(contentElement);

            (global.document.body || editor.container).appendChild(element);
            return element;
        }

        function renderer() {
            if (!markdownRenderer) {
                markdownRenderer = global.AutoJsAceTooltip.loadMarkdownRenderer();
                markdownLoaded = true;
            }
            return markdownRenderer;
        }

        function hide() {
            if (hoverTimer !== null) {
                clearTimeout(hoverTimer);
                hoverTimer = null;
            }
            if (element) {
                element.style.display = "none";
            }
            visible = false;
        }

        function viewportSize() {
            var doc = global.document && global.document.documentElement || {};
            return {
                width: Math.max(Number(global.innerWidth) || 0, Number(doc.clientWidth) || 0, 320),
                height: Math.max(Number(global.innerHeight) || 0, Number(doc.clientHeight) || 0, 240)
            };
        }

        function positionElement(point) {
            var box = ensureElement();
            var viewport = viewportSize();
            var width = box.offsetWidth || 320;
            var height = box.offsetHeight || 120;
            var x = Math.max(EDGE_PADDING, Number(point.x) || EDGE_PADDING);
            var y = Math.max(EDGE_PADDING, Number(point.y) || EDGE_PADDING);
            var left = Math.min(x + 12, viewport.width - width - EDGE_PADDING);
            var top = Math.min(y + 18, viewport.height - height - EDGE_PADDING);
            if (top < EDGE_PADDING) {
                top = EDGE_PADDING;
            }
            if (left < EDGE_PADDING) {
                left = EDGE_PADDING;
            }
            box.style.left = Math.round(left) + "px";
            box.style.top = Math.round(top) + "px";
        }

        function screenPointFor(pos) {
            var rangeStart = normalizePosition(pos);
            var screen = editor && editor.renderer && editor.renderer.textToScreenCoordinates ?
                editor.renderer.textToScreenCoordinates(rangeStart.row, rangeStart.column) :
                null;
            return {
                x: screen ? (Number(screen.pageX) || 0) - (Number(global.pageXOffset) || 0) : EDGE_PADDING,
                y: screen ? (Number(screen.pageY) || 0) - (Number(global.pageYOffset) || 0) : EDGE_PADDING
            };
        }

        function showHover(hover, point) {
            if (!hover || !hover.docText) {
                hide();
                return false;
            }
            var box = ensureElement();
            captionElement.textContent = hover.caption || hover.value || "AutoJs6";
            contentElement.innerHTML = renderer()(hover.docText);
            box.style.display = "block";
            visible = true;
            positionElement(point || screenPointFor(hover.range && hover.range.start));
            return true;
        }

        function getHover(pos) {
            if (typeof config.getHover !== "function") {
                return null;
            }
            return config.getHover(normalizePosition(pos));
        }

        function showAtPosition(pos) {
            try {
                pos = normalizePosition(pos);
                return showHover(getHover(pos), screenPointFor(pos));
            } catch (error) {
                notify(config, "ACE tooltip hover failed: " + error, error);
                hide();
                return false;
            }
        }

        function scheduleHover(pos) {
            if (hoverTimer !== null) {
                clearTimeout(hoverTimer);
            }
            hoverTimer = setTimeout(function() {
                hoverTimer = null;
                showAtPosition(pos);
            }, hoverDelayMs);
        }

        function onMouseMove(event) {
            if (isTouchEvent(event)) {
                hide();
                return;
            }
            if (!event || typeof event.getDocumentPosition !== "function") {
                return;
            }
            scheduleHover(event.getDocumentPosition());
        }

        function attach() {
            if (attached || !editor || !editor.on) {
                return;
            }
            editor.on("mousemove", onMouseMove);
            editor.on("mousedown", hide);
            editor.on("changeSelection", hide);
            if (session && session.on) {
                session.on("change", hide);
                session.on("changeScrollTop", hide);
                session.on("changeScrollLeft", hide);
            }
            if (editor.container && editor.container.addEventListener) {
                editor.container.addEventListener("mouseleave", hide);
                editor.container.addEventListener("touchstart", hide);
                editor.container.addEventListener("wheel", hide);
            }
            attached = true;
        }

        function detach() {
            hide();
            if (attached && editor && editor.off) {
                editor.off("mousemove", onMouseMove);
                editor.off("mousedown", hide);
                editor.off("changeSelection", hide);
            }
            if (attached && session && session.off) {
                session.off("change", hide);
                session.off("changeScrollTop", hide);
                session.off("changeScrollLeft", hide);
            }
            if (attached && editor && editor.container && editor.container.removeEventListener) {
                editor.container.removeEventListener("mouseleave", hide);
                editor.container.removeEventListener("touchstart", hide);
                editor.container.removeEventListener("wheel", hide);
            }
            attached = false;
        }

        function destroy() {
            detach();
            if (element && element.parentNode) {
                element.parentNode.removeChild(element);
            }
            element = null;
            captionElement = null;
            contentElement = null;
        }

        function getState() {
            return {
                attached: attached,
                visible: visible,
                domCreated: !!element,
                markdownLoaded: markdownLoaded,
                markdownRenderer: markdownLoaded ? "safe-subset" : "not-loaded",
                hoverDelayMs: hoverDelayMs
            };
        }

        attach();

        return {
            attach: attach,
            detach: detach,
            destroy: destroy,
            hide: hide,
            showAtPosition: showAtPosition,
            renderMarkdown: function(text) {
                markdownLoaded = true;
                return renderer()(text);
            },
            getState: getState
        };
    }

    global.AutoJsAceTooltip = {
        createController: createController,
        install: createController,
        loadMarkdownRenderer: function() {
            return renderMarkdown;
        },
        renderMarkdown: renderMarkdown,
        escapeHtml: escapeHtml
    };
})(window);
