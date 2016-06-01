# JSweet HTTP Server

This project provides light HTTP server with a JSON Rest Web API to run the JSweet transpiler from any client, without having to install JSweet locally.

The server is built with [NanoHttpd](https://github.com/NanoHttpd/nanohttpd).

It comes also with a small JSweet client, which is the JSweet sandbox available at http://www.jsweet.org/jsweet-live-sandbox/. JSweet provides a default Web Service, which is publically available: http://sandbox.jsweet.org/transpile.

## How to use

Here is a basic client written in JSweet:

```java
import static jsweet.dom.Globals.console;
import jsweet.dom.FormData;
import jsweet.dom.XMLHttpRequest;
import jsweet.lang.JSON;
import jsweet.lang.Math;
[...]

public class JSweetClient {
	static final String SERVER_URL = ...;
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
