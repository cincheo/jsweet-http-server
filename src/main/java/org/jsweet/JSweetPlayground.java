/* 
 * Copyright (C) 2015 CINCHEO SAS
 */
package org.jsweet;

import static def.codemirror.codemirror.Globals.fromTextArea;
import static def.jquery.Globals.$;
import static jsweet.dom.Globals.console;
import static jsweet.dom.Globals.document;
import static jsweet.dom.Globals.alert;
import static jsweet.util.Globals.array;
import static jsweet.util.Globals.number;
import static jsweet.util.Globals.string;

import java.util.function.Supplier;

import def.codemirror.codemirror.EditorConfiguration;
import def.codemirror.codemirror.EditorFromTextArea;
import jsweet.dom.Event;
import jsweet.dom.FormData;
import jsweet.dom.HTMLFormElement;
import jsweet.dom.HTMLInputElement;
import jsweet.dom.HTMLSelectElement;
import jsweet.dom.HTMLTextAreaElement;
import jsweet.dom.XMLHttpRequest;
import jsweet.lang.JSON;
import jsweet.lang.Math;

/**
 * Editor controller
 * 
 * @author Louis Grignon
 * @author Renaud Pawlak
 */
class JSweetLiveEditor {

	public static String SERVICE_URL = "/transpile";

	public static JSweetLiveEditor instance;

	private EditorFromTextArea javaEditor;
	private EditorFromTextArea jsEditor;
	private String jsCode;
	private String tsCode;
	private boolean showJs = true;

	private XMLHttpRequest currentRequest;
	private String lastJavaCode;

	public JSweetLiveEditor() {
		// singleton is only for debug
		instance = this;
	}

	public void initialize() {
		console.log("initializing JSweetLiveEditor");

		console.log("initializing transpile button");
		$("#transpileButton").click(e -> {
			console.log("transpile button clicked");
			this.transpileJavaToJs(true);

			return false;
		});

		console.log("initializing java editor");
		javaEditor = fromTextArea((HTMLTextAreaElement) document.getElementById("javaEditor"),
				new EditorConfiguration() {
					{
						extraKeys = new Object() {
							{
								$set("Ctrl-S", onEditorSaved);
								$set("Cmd-S", onEditorSaved);
							}
						};
						mode = "text/x-java";
						lineNumbers = true;
						lineWrapping = false;
						indentWithTabs = true;
						indentUnit = 4;
						theme = "default";
					}
				});
		// TODO translator
		// javaEditor.setOption("matchBrackets", "true");

		console.log("initializing js editor");
		jsEditor = fromTextArea((HTMLTextAreaElement) document.getElementById("jsEditor"), new EditorConfiguration() {
			{
				mode = "text/typescript";
				lineNumbers = true;
				lineWrapping = false;
				indentWithTabs = true;
				theme = "default";
				readOnly = true;
			}
		});

		this.transpileJavaToJs(false);
	}

	private Runnable onEditorSaved = () -> {
		console.log("save requested");
		transpileJavaToJs(false);
	};

	// TODO: BUG because of alert name clash!!!! We need to check local
	// variables also? (should be prefixed by this??)

	private void transpileJavaToJs(boolean alertUser) {

		if (this.lastJavaCode == null) {
			this.lastJavaCode = "";
		}

		String javaCode = this.javaEditor.getDoc().getValue().trim();
		if (javaCode == "") {
			if (alertUser) {
				alert("Please enter some Java code on the left-hand editor.");
			}
			console.log("no java code to be transpiled");
			return;
		}

		if (javaCode.trim() == this.lastJavaCode.trim()) {
			if (alertUser) {
				alert("No changes found, tranpilation skipped.");
			}
			console.log("did not modified, just doing nothing");
			return;
		}

		this.lastJavaCode = javaCode;
		jsEditor.getDoc().setValue("Loading.......");

		if (currentRequest != null) {
			currentRequest.abort();
		}

		currentRequest = new XMLHttpRequest();
		currentRequest.open("POST", SERVICE_URL, true);
		currentRequest.onload = (e) -> {

			JSweetServerTranspilationResponse response = (JSweetServerTranspilationResponse) JSON
					.parse(currentRequest.responseText);

			currentRequest = null;

			String jsContent = response.jsout;
			String tsContent = response.tsout;
			String messageColor = "green";
			String message = "Success!";
			if (!response.success) {
				if (response.errorMessage != null && response.errorMessage != "") {
					message = response.errorMessage;
				} else {
					message = response.errors.length + " error" + (response.errors.length > 1 ? "s" : "");
				}
				messageColor = "red";

				jsContent = array(response.errors).join("\n");
				tsContent = array(response.errors).join("\n");
			}

			setJsMessage(messageColor, message);
			jsCode = jsContent;
			tsCode = tsContent;
			showOutput();
			return null;
		};

		currentRequest.onerror = (e) -> {

			console.error("error " + currentRequest.status + ": " + currentRequest.statusText);
			setJsMessage("red", "Request error: " + currentRequest.statusText);

			currentRequest = null;

			return null;
		};

		FormData data = new FormData();
		data.append("javaCode", javaCode);
		data.append("tid", getTransactionUuid());
		data.append("tsout", "true");

		try {
			currentRequest.send(data);
		} catch (Exception requestError) {
			console.error(requestError);
		}
	}

	private void setJsMessage(String messageColor, String message) {
		$("#jsdetails").html("<span style=\"color: " + messageColor + ";\">" + message + "</span>");
	}

	private String transactionId;

	private String getTransactionUuid() {
		if (transactionId == null) {
			transactionId = generateUuid();
		}
		return transactionId;
	}

	private static String generateUuid() {
		Supplier<String> s4 = () -> {
			return number(Math.floor((1 + Math.random()) * 0x10000)).toString(16).substring(1);
		};

		return s4.get() + s4.get() + '-' + s4.get() + '-' + s4.get() + '-' + s4.get() + '-' + s4.get() + s4.get()
				+ s4.get();
	}

	private void showOutput() {
		if (showJs) {
			jsEditor.getDoc().setValue(jsCode);
		} else {
			jsEditor.getDoc().setValue(tsCode);
		}
	}

	public void switchOutput() {
		showJs = !showJs;
		if (showJs) {
			document.getElementById("switchJsTs").innerHTML = "Show TypeScript";
			document.getElementById("languageName").innerHTML = "JavaScript";
		} else {
			document.getElementById("switchJsTs").innerHTML = "Show JavaScript";
			document.getElementById("languageName").innerHTML = "TypeScript";
		}
		showOutput();
	}

	public void run() {
		((HTMLInputElement) document.getElementById("codeToRun")).value = jsCode;
		((HTMLFormElement) document.forms.item("run")).submit();
	}

	public void selectExample(Event event) {
		String code = document.getElementById(((HTMLSelectElement) event.target).value).innerHTML;
		javaEditor.getDoc().setValue(code.substring(4, (int) string(code).length - 3));
		transpileJavaToJs(false);
	}

}

/**
 * Entry point, instantiate and initialize the editor
 * 
 * @author Louis Grignon
 * @author Renaud Pawlak
 */
public class JSweetPlayground {
	static JSweetLiveEditor editor;

	public static void main(String[] args) {
		editor = new JSweetLiveEditor();
		$(document).ready(() -> {
			editor.initialize();
			return null;
		});
	}

	public static void switchOutput() {
		editor.switchOutput();
	}

	public static void run() {
		editor.run();
	}

	public static void selectExample(Event event) {
		editor.selectExample(event);
	}

}
