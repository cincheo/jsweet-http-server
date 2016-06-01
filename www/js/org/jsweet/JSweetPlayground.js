"Generated from Java with JSweet 1.0.0-SNAPSHOT - http://www.jsweet.org";
var org;
(function (org) {
    var jsweet;
    (function (jsweet) {
        var fromTextArea = CodeMirror.fromTextArea;
        /**
         * Editor controller
         *
         * @author Louis Grignon
         * @author Renaud Pawlak
         */
        var JSweetLiveEditor = (function () {
            function JSweetLiveEditor() {
                var _this = this;
                this.showJs = true;
                this.onEditorSaved = function () {
                    console.log("save requested");
                    _this.transpileJavaToJs(false);
                };
                JSweetLiveEditor.instance = this;
            }
            JSweetLiveEditor.prototype.initialize = function () {
                var _this = this;
                console.log("initializing JSweetLiveEditor");
                console.log("initializing transpile button");
                $("#transpileButton").click(function (e) {
                    console.log("transpile button clicked");
                    _this.transpileJavaToJs(true);
                    return false;
                });
                console.log("initializing java editor");
                this.javaEditor = fromTextArea(document.getElementById("javaEditor"), {
                    extraKeys: (function (target) {
                        target["Ctrl-S"] = _this.onEditorSaved;
                        target["Cmd-S"] = _this.onEditorSaved;
                        return target;
                    })(new Object()),
                    mode: "text/x-java",
                    lineNumbers: true,
                    lineWrapping: false,
                    indentWithTabs: true,
                    indentUnit: 4,
                    theme: "default"
                });
                console.log("initializing js editor");
                this.jsEditor = fromTextArea(document.getElementById("jsEditor"), {
                    mode: "text/typescript",
                    lineNumbers: true,
                    lineWrapping: false,
                    indentWithTabs: true,
                    theme: "default",
                    readOnly: true
                });
                this.transpileJavaToJs(false);
            };
            JSweetLiveEditor.prototype.transpileJavaToJs = function (alertUser) {
                var _this = this;
                if ((this.lastJavaCode == null)) {
                    this.lastJavaCode = "";
                }
                var javaCode = this.javaEditor.getDoc().getValue().trim();
                if ((javaCode == "")) {
                    if ((alertUser)) {
                        alert("Please enter some Java code on the left-hand editor.");
                    }
                    console.log("no java code to be transpiled");
                    return;
                }
                if ((javaCode.trim() == this.lastJavaCode.trim())) {
                    if ((alertUser)) {
                        alert("No changes found, tranpilation skipped.");
                    }
                    console.log("did not modified, just doing nothing");
                    return;
                }
                this.lastJavaCode = javaCode;
                this.jsEditor.getDoc().setValue("Loading.......");
                if ((this.currentRequest != null)) {
                    this.currentRequest.abort();
                }
                this.currentRequest = new XMLHttpRequest();
                this.currentRequest.open("POST", JSweetLiveEditor.SERVICE_URL, true);
                this.currentRequest.onload = function (e) {
                    var response = JSON.parse(_this.currentRequest.responseText);
                    _this.currentRequest = null;
                    var jsContent = response.jsout;
                    var tsContent = response.tsout;
                    var messageColor = "green";
                    var message = "Success!";
                    if ((!response.success)) {
                        if ((response.errorMessage != null && response.errorMessage != "")) {
                            message = response.errorMessage;
                        }
                        else {
                            message = response.errors.length + " error" + (response.errors.length > 1 ? "s" : "");
                        }
                        messageColor = "red";
                        jsContent = (response.errors).join("\n");
                        tsContent = (response.errors).join("\n");
                    }
                    _this.setJsMessage(messageColor, message);
                    _this.jsCode = jsContent;
                    _this.tsCode = tsContent;
                    _this.showOutput();
                    return null;
                };
                this.currentRequest.onerror = function (e) {
                    console.error("error " + _this.currentRequest.status + ": " + _this.currentRequest.statusText);
                    _this.setJsMessage("red", "Request error: " + _this.currentRequest.statusText);
                    _this.currentRequest = null;
                    return null;
                };
                var data = new FormData();
                data.append("javaCode", javaCode);
                data.append("tid", this.getTransactionUuid());
                data.append("tsout", "true");
                try {
                    this.currentRequest.send(data);
                }
                catch (requestError) {
                    console.error(requestError);
                }
                ;
            };
            JSweetLiveEditor.prototype.setJsMessage = function (messageColor, message) {
                $("#jsdetails").html("<span style=\"color: " + messageColor + ";\">" + message + "</span>");
            };
            JSweetLiveEditor.prototype.getTransactionUuid = function () {
                if ((this.transactionId == null)) {
                    this.transactionId = JSweetLiveEditor.generateUuid();
                }
                return this.transactionId;
            };
            JSweetLiveEditor.generateUuid = function () {
                var s4 = function () {
                    return (Math.floor((1 + Math.random()) * 65536)).toString(16).substring(1);
                };
                return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
            };
            JSweetLiveEditor.prototype.showOutput = function () {
                if ((this.showJs)) {
                    this.jsEditor.getDoc().setValue(this.jsCode);
                }
                else {
                    this.jsEditor.getDoc().setValue(this.tsCode);
                }
            };
            JSweetLiveEditor.prototype.switchOutput = function () {
                this.showJs = !this.showJs;
                if ((this.showJs)) {
                    document.getElementById("switchJsTs").innerHTML = "Show TypeScript";
                    document.getElementById("languageName").innerHTML = "JavaScript";
                }
                else {
                    document.getElementById("switchJsTs").innerHTML = "Show JavaScript";
                    document.getElementById("languageName").innerHTML = "TypeScript";
                }
                this.showOutput();
            };
            JSweetLiveEditor.prototype.run = function () {
                document.getElementById("codeToRun").value = this.jsCode;
                document.forms.item("run").submit();
            };
            JSweetLiveEditor.prototype.selectExample = function (event) {
                var code = document.getElementById(event.target.value).innerHTML;
                this.javaEditor.getDoc().setValue(code.substring(4, code.length - 3));
                this.transpileJavaToJs(false);
            };
            JSweetLiveEditor.SERVICE_URL = "/transpile";
            return JSweetLiveEditor;
        })();
        jsweet.JSweetLiveEditor = JSweetLiveEditor;
        /**
         * Entry point, instantiate and initialize the editor
         *
         * @author Louis Grignon
         * @author Renaud Pawlak
         */
        var JSweetPlayground = (function () {
            function JSweetPlayground() {
            }
            JSweetPlayground.main = function (args) {
                JSweetPlayground.editor = new JSweetLiveEditor();
                $(document).ready(function () {
                    JSweetPlayground.editor.initialize();
                    return null;
                });
            };
            JSweetPlayground.switchOutput = function () {
                JSweetPlayground.editor.switchOutput();
            };
            JSweetPlayground.run = function () {
                JSweetPlayground.editor.run();
            };
            JSweetPlayground.selectExample = function (event) {
                JSweetPlayground.editor.selectExample(event);
            };
            return JSweetPlayground;
        })();
        jsweet.JSweetPlayground = JSweetPlayground;
    })(jsweet = org.jsweet || (org.jsweet = {}));
})(org || (org = {}));
org.jsweet.JSweetPlayground.main(null);
