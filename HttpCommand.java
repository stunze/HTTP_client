public enum HttpCommand {
	HEAD("HEAD"), GET("GET"), PUT("PUT"), POST("POST");
	
	String command;
	
	HttpCommand(String commandString) {
		this.command = commandString;
	}
	
	public String getCommandString() {
		return this.command;
	}
}