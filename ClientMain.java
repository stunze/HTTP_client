import java.io.File;
import java.util.Scanner;


public class ClientMain {
	
	public static void main(String[] args) {
		if(args.length != 3) {
			System.out.println("Invalid use of chatclient.");
			System.out.println("Usage: ClientMain [HTTPCommand] [URI] [PORT]");
			return;
		}
		
		String[] parsedUrl = UrlParser.parse(args[1].trim());
		String input = "";
		HttpCommand command;
		switch(args[0].toUpperCase().trim()) {
			case "GET":
				command = HttpCommand.GET;
				break;
			case "HEAD":
				command = HttpCommand.HEAD;
				break;
			case "POST":
				command = HttpCommand.POST;
				input = getShellInput("Input the data of the POST command\nFormat: key=value, 1 per line\nEnd the data with an empty line\n--------------------");
				input = input.replace("\r\n", "&").replace(" ", "+");
				break;
			case "PUT":
				command = HttpCommand.PUT;
				input = getShellInput("Input the data of the PUT command\nEnd the file with an empty line\n--------------------");
				break;
			default:
				throw new RuntimeException("Invalid request type.");
		}
		
		HttpClient client = new HttpClient(parsedUrl[0], command, Integer.valueOf(args[2]).intValue());
		File outputPath = new File("/home/tuur/Desktop/http/" + parsedUrl[0].replace(".", "_") + parsedUrl[1].replace(".", "_"));
		client.setOutputPath(outputPath);
		
		client.sendRequest(parsedUrl[1], parsedUrl[2], input);
		client.closeConnection();
	}
	
	public static String getShellInput(String message) {
		Scanner sc = new Scanner(System.in);
		StringBuilder sBuilder = new StringBuilder();
		
		System.out.println(message);
		
		String in;
		while(!(in = sc.nextLine()).equals("")){
			sBuilder.append(in+"\r\n");
		}
		sc.close();
		
		String input = sBuilder.toString();
		return input.substring(0, input.length()-2);
	}
	
	
}