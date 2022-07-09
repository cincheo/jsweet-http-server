package org.jsweet.webapi;

public class ServerLauncher {
	
	public static void main(String[] args) {

		System.out.println("starting server");
		System.out.println("java version: " + System.getProperty("java.version"));
		Thread serverThread = new Thread() {
			@Override
			public void run() {
				try {
					Server server = new Server();
					System.out.println("server=" + server);

					synchronized (this) {
						wait();
					}
				} catch (Exception ioe) {
					System.out.println("server failed");
					ioe.printStackTrace();
				}
			}
		};
		serverThread.setDaemon(false);
		serverThread.start();

	}
}
