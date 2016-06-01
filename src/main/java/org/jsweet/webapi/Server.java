/* 
 * Copyright (C) 2015 CINCHEO SAS
 */
package org.jsweet.webapi;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.join;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.jsweet.JSweetServerTranspilationResponse;
import org.jsweet.transpiler.EcmaScriptComplianceLevel;
import org.jsweet.transpiler.JSweetProblem;
import org.jsweet.transpiler.JSweetTranspiler;
import org.jsweet.transpiler.ModuleKind;
import org.jsweet.transpiler.Severity;
import org.jsweet.transpiler.SourceFile;
import org.jsweet.transpiler.SourcePosition;
import org.jsweet.transpiler.TranspilationHandler;
import org.jsweet.transpiler.util.ConsoleTranspilationHandler;

import com.google.gson.GsonBuilder;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * Compiler web service
 * 
 * @author Louis Grignon
 * @author Renaud Pawlak
 */
public class Server extends NanoHTTPD {

	private final static DateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private final static Logger logger = Logger.getLogger(Server.class);

	private final static String WWW_DIR = "www";

	private final static Pattern PACKAGE_PATTERN = Pattern.compile("^package (.*);$", Pattern.MULTILINE);

	public Server() throws IOException {
		super(8580);
		setTempFileManagerFactory(new TempFileManagerFactory() {

			@Override
			public TempFileManager create() {
				return new DefaultTempFileManager();
			}
		});
		start();

		logger.info("...\nRunning! Point your browers to http://localhost:" + getListeningPort() + "/ \n");
	}

	@Override
	public Response serve(IHTTPSession session) {

		Response response;
		if (session.getMethod() == Method.POST && session.getUri().equals("/transpile")) {
			response = serveTranspile(session);
		} else if (session.getUri().equals("/run")) {
			response = serveRun(session);
		} else {
			response = serveStaticResource(session);
		}

		response.addHeader("Access-Control-Allow-Origin", "*");

		return response;
	}

	private Response serveStaticResource(IHTTPSession session) {
		String uri = session.getUri();
		if (isBlank(uri) || uri.equals("/")) {
			uri = "/index.html";
		}
		if (!uri.startsWith("/")) {
			uri = "/" + uri;
		}
		String extension = FilenameUtils.getExtension(uri);
		File resource = new File("./" + WWW_DIR + "/" + uri);
		logger.info("rendering " + resource + " with path " + uri + ", root dir: " + new File("").getAbsolutePath());
		if (!resource.exists()) {
			logger.error("cannot render " + resource + ": does not exist", new Exception());
			return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "resource does not exist");
		}

		try {
			return newFixedLengthResponse(Response.Status.OK, "text/" + extension, new FileInputStream(resource),
					resource.length());
		} catch (FileNotFoundException e) {
			logger.error("page could not be rendered", e);
			return newFixedLengthResponse("ERROR: " + e);
		}
	}

	private final Map<String, Thread> transpilationThreads = new HashMap<>();

	private void registerTranspilationThread(String transactionId, Thread thread) {
		synchronized (transpilationThreads) {
			try {
				Thread previousThread = transpilationThreads.get(transactionId);
				if (previousThread.isAlive()) {
					logger.debug("interrupt previous thread");
					previousThread.interrupt();
				}
			} catch (Exception e) {
				logger.warn("could not interrupt concurrent transpilation thread, ignored", e);
			}
			transpilationThreads.put(transactionId, thread);
		}
	}

	private Response serveRun(IHTTPSession session) {
		Map<String, String> files = new HashMap<String, String>();
		try {
			session.parseBody(files);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		logger.info("parmsNames=" + session.getParms().keySet());
		return newFixedLengthResponse(
				"<html><head><script src=\"jquery-1.11.3.min.js\"></script><script src=\"js/j4ts-0.1.0/bundle.js\"></script></head><body><script>"
						+ session.getParms().get("code") + "</script></body></html>");
	}

	private Response serveTranspile(IHTTPSession session) {

		logger.info("compilation request from " + session.getHeaders().get("remote-addr") + " allHeaders="
				+ session.getHeaders());
		Date startTime = new Date();

		JSweetServerTranspilationResponse response = new JSweetServerTranspilationResponse();

		try {
			response.startTime = OUTPUT_DATE_FORMAT.format(startTime);

			logger.debug("start parse body");
			Map<String, String> files = new HashMap<String, String>();
			session.parseBody(files);

			logger.info("method=" + session.getMethod());
			logger.debug("parmsNames=" + session.getParms().keySet());
			logger.trace("parms=" + session.getParms());
			logger.debug("queryParameterString=" + session.getQueryParameterString());

			response.transactionId = session.getParms().get("tid");
			registerTranspilationThread(response.transactionId, Thread.currentThread());
			logger.info("transactionId=" + response.transactionId);

			String javaCode = session.getParms().get("javaCode");
			logger.trace("javaCode=" + javaCode);

			if (Thread.currentThread().isInterrupted()) {
				return newAbortedResponse();
			}

			List<File> javaFiles = writeJavaFiles(javaCode, response);
			if (javaFiles.size() > 0) {
				if (Thread.currentThread().isInterrupted()) {
					return newAbortedResponse();
				}

				applyJSweetMagic(session.getParms(), javaFiles, response);

				Date endTime = new Date();
				response.endTime = OUTPUT_DATE_FORMAT.format(endTime);
				response.durationMillis = endTime.getTime() - startTime.getTime();

				logger.info("transpilation done in " + response.durationMillis + "ms");
			}
		} catch (Throwable t) {
			logger.error("critical error during transpilation", t);
			response.errorMessage = "<technical error, please contact JSweet support>";
		}

		String jsonResponse = new GsonBuilder() //
				.setPrettyPrinting() //
				.create() //
				.toJson(response);

		if (Thread.currentThread().isInterrupted()) {
			return newAbortedResponse();
		}

		logger.info("jsonResponse=" + jsonResponse + " response=" + response);

		return newFixedLengthResponse(jsonResponse);
	}

	/**
	 * Splits java code into separate files (file name must match public class
	 * name)
	 */
	private List<File> writeJavaFiles(String javaCode, JSweetServerTranspilationResponse response) throws IOException {
		if (javaCode == null) {
			javaCode = "";
		}
		if (isBlank(javaCode)) {
			logger.info("java code empty, nothing to transpile");
			response.errorMessage = "Java code is empty";
			return emptyList();
		}
		if (javaCode.length() > 100000) {
			logger.warn("java code too long: " + javaCode.length());
			response.errorMessage = "Java code is too long";
			return emptyList();
		}

		// package
		Matcher packageMatcher = PACKAGE_PATTERN.matcher(javaCode);
		response.packageName = "_";
		if (packageMatcher.find()) {
			response.packageName = packageMatcher.group(1);
		}
		logger.trace("package=" + response.packageName);

		// prints classes
		String packagePath = join(response.packageName.split("[.]"), "/");
		File outDir = new File(getOutputDir(response.transactionId) + "/java/" + packagePath);
		logger.debug("java output dir: " + outDir);
		outDir.mkdirs();

		List<File> classFiles = new LinkedList<>();
		String className = "Tmp";
		File outFile = new File(outDir, className + ".java");
		logger.trace("writing " + className + " to " + outFile);
		logger.trace("code=" + response);

		FileUtils.write(outFile, javaCode);

		classFiles.add(outFile);

		return classFiles;
	}

	private File getOutputDir(String transactionId) {
		return new File(FileUtils.getTempDirectory(), "JSweetCloudTranspiler/" + transactionId);
	}

	private void applyJSweetMagic(Map<String, String> parameters, List<File> javaFiles,
			JSweetServerTranspilationResponse response) throws IOException {

		String transactionId = response.transactionId;

		JSweetTranspiler transpiler = createTranspiler(transactionId);

		ServerTranspilationHandler transpilationHandler = new ServerTranspilationHandler(
				new ConsoleTranspilationHandler());

		SourceFile[] sources = SourceFile.getSourceFiles(javaFiles);

		logger.info("transpiling " + javaFiles);
		transpiler.transpile(transpilationHandler, sources);

		StringBuilder outBuilder;

		if ("true".equals(parameters.get("tsout"))) {
			outBuilder = new StringBuilder();
			for (SourceFile sourceFile : sources) {
				if (sourceFile.getTsFile() == null) {
					logger.warn("ts file does not exist for " + sourceFile);
				} else {
					boolean firstLine = true;
					for (String line : FileUtils.readLines(sourceFile.getTsFile())) {
						if ("true".equals(parameters.get("removeFirstLine")) && firstLine) {
							firstLine = false;
							continue;
						}
						outBuilder.append(line).append("\n");
					}
				}
			}
			response.tsout = outBuilder.toString();
		}

		outBuilder = new StringBuilder();
		for (SourceFile sourceFile : sources) {
			if (sourceFile.getJsFile() == null) {
				logger.warn("js file does not exist for " + sourceFile);
			} else {
				boolean firstLine = true;
				for (String line : FileUtils.readLines(sourceFile.getJsFile())) {
					if ("true".equals(parameters.get("removeFirstLine")) && firstLine) {
						firstLine = false;
						continue;
					}
					outBuilder.append(line).append("\n");
				}
			}
		}
		response.jsout = outBuilder.toString();

		response.errors = transpilationHandler.getErrors().toArray(new String[0]);
		response.success = transpilationHandler.getErrors().isEmpty();

		if (!response.success) {
			response.jsout = "";
		}
	}

	private static class ServerTranspilationHandler implements TranspilationHandler {

		private final List<String> errors = new LinkedList<>();
		private TranspilationHandler delegate;

		public ServerTranspilationHandler(TranspilationHandler delegate) {
			this.delegate = delegate;
		}

		@Override
		public void onCompleted(JSweetTranspiler transpiler, boolean fullPass, SourceFile[] files) {
			delegate.onCompleted(transpiler, fullPass, files);
		}

		@Override
		public void report(JSweetProblem problem, SourcePosition sourcePosition, String message) {
			// TODO: find a way to report warnings
			if (problem.getSeverity() == Severity.ERROR) {
				String positionPrefix = "<unknown>";
				if (sourcePosition != null) {
					// could add the column, but most of the time incorrect
					// because
					// of tabs
					positionPrefix = "Line " + sourcePosition.getStartLine();
				}

				errors.add(positionPrefix + ": " + message);
			}
			delegate.report(problem, sourcePosition, message);
		}

		public List<String> getErrors() {
			return errors;
		}
	};

	private JSweetTranspiler createTranspiler(String transactionId) {

		logger.info("initializing transpiler");

		File workingDir = new File(getOutputDir(transactionId), "/jsweet");
		workingDir.mkdirs();

		File tsOut = new File(workingDir, "ts");
		tsOut.mkdirs();

		File jsOut = new File(workingDir, "js");
		jsOut.mkdirs();

		String classPath = System.getProperty("java.class.path");

		EcmaScriptComplianceLevel targetVersion = EcmaScriptComplianceLevel.ES3;
		ModuleKind module = ModuleKind.none;
		String encoding = "UTF-8";
		boolean verbose = false;
		boolean sourceMaps = false;
		boolean noRootDirectories = false;
		boolean enableAssertions = true;
		String jdkHome = System.getProperty("java.home");

		logger.debug("jsOut: " + jsOut);
		logger.debug("tsOut: " + tsOut);
		logger.debug("ecmaTargetVersion: " + targetVersion);
		logger.debug("moduleKind: " + module);
		logger.debug("sourceMaps: " + sourceMaps);
		logger.debug("verbose: " + verbose);
		logger.debug("noRootDirectories: " + noRootDirectories);
		logger.debug("enableAssertions: " + enableAssertions);
		logger.debug("encoding: " + encoding);
		logger.debug("jdkHome: " + jdkHome);

		JSweetTranspiler transpiler = new JSweetTranspiler(workingDir, tsOut, jsOut, new File(WWW_DIR + "/js"),
				classPath);
		transpiler.setTscWatchMode(false);
		transpiler.setEcmaTargetVersion(targetVersion);
		transpiler.setModuleKind(module);
		transpiler.setBundle(false);
		// transpiler.setBundlesDirectory(StringUtils.isBlank(bundlesDirectory)
		// ? null : new File(bundlesDirectory));
		transpiler.setPreserveSourceLineNumbers(sourceMaps);
		transpiler.setEncoding(encoding);
		transpiler.setNoRootDirectories(noRootDirectories);
		transpiler.setIgnoreAssertions(!enableAssertions);
		transpiler.setIgnoreJavaFileNameError(true);

		logger.info("transpiler initialized");

		return transpiler;
	}

	private Response newAbortedResponse() {
		logger.debug("request aborted");
		return newFixedLengthResponse(Status.REQUEST_TIMEOUT, "text/plain", "cancelled by concurrent request");
	}

	@Override
	public String toString() {
		return "JSweet server listening on " + getListeningPort() + " - current directory: "
				+ new File(".").getAbsolutePath();
	}

}
