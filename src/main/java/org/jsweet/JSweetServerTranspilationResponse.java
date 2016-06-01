package org.jsweet;

/**
 * Compilation result object contract
 * 
 * @author Louis Grignon
 * @author Renaud Pawlak
 */
public class JSweetServerTranspilationResponse {
	public String startTime;
	public String transactionId;
	public long sourceLength;
	public boolean success;
	public String endTime;
	public long durationMillis;

	public String[] errors;
	public String errorMessage;
	public String jsout;
	public String tsout;
	public String packageName;
}