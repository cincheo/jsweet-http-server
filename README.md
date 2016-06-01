# JSweet HTTP Server

This project provides light HTTP server with a JSON Rest Web API to run the JSweet transpiler from any client, without having to install JSweet locally.

The server is built with [NanoHttpd](https://github.com/NanoHttpd/nanohttpd).

It comes also with a small JSweet client, which is the JSweet sandbox. You can try it live at http://www.jsweet.org/jsweet-live-sandbox/. 

## How to use

To transpile a file from Java to TypeScript or JavaScript, one must invoke the ``transpile`` Web Service on the JSweet server.

The JSweet team has publicly available server at: http://sandbox.jsweet.org.

Here is a basic client written in JSweet:

```java
import static jsweet.dom.Globals.console;
import jsweet.dom.FormData;
import jsweet.dom.XMLHttpRequest;
import jsweet.lang.JSON;
import jsweet.lang.Math;
[...]

public class JSweetClient {
	// use your own server if you need to
	static final String SERVER_URL = "http://sandbox.jsweet.org";
	public static void doInvoke() { 
		// the actual service invocation
		XMLHttpRequest currentRequest = new XMLHttpRequest();
		currentRequest.open("POST", SERVER_URL + "/transpile", true);
		currentRequest.onload = (e) -> {
			JSweetServerTranspilationResponse response = (JSweetServerTranspilationResponse) JSON
					.parse(currentRequest.responseText);
			if (response.success) {
				// JavaScript output
  				String jsContent = response.jsout;
				// TypeScript output
				String tsContent = response.tsout;
				// do whatever with the result
				[...]
			}
			return null;
		};
		currentRequest.onerror = (e) -> {
		  [...]
			return null;
		};
		FormData data = new FormData();
		// here the actual code to transpile
		data.append("javaCode", "public class C {}");
		// should be unique
		data.append("tid", "" + Math.random());
		// set to false if you only want the JavaScript output
		data.append("tsout", "true");
		try {
			currentRequest.send(data);
		} catch (Exception requestError) {
			console.error(requestError);
		}
	}
}
```

# Build and run the server

Before compiling, you can edit the ``pom.xml`` to add dependencies to your server, so that your service can transpile code using any of the available candies listed [here](http://www.jsweet.org/candies-releases/).

To compile the server, use Maven in the project's directory:

```
> mvn clean compile
```

To run the service under Unix-based OS (would need to be adapted for other OS):

```
> ./run.sh
```

# Build and run the client

To compile the client, use Maven in the project's directory:

```
mvn -P client generate-sources
```

Start the client:

```
> firefox www/index.html
```
