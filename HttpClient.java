import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO if-modified-since toevoegen
// ex. "If-Modified-Since: Tue, 05 Jul 2016 23:27:52 GMT\r\n"

public class HttpClient {

	private Socket socket;
	private int port;
	
	private String host = "";
	
	private HttpCommand command;
	
	private PrintWriter httpPrintWriter;
	private FileOutputStream fileOutputStream;

	
	private int inputBufferLength = 4096; 
	
	private File outputPath;
	private File outputFile;
	
	private final static String LINE_SEPARATOR_STRING = "\r\n";
	private final static int LINE_SEPARATOR = 0x0d0a;
	private final static int HEADER_SEPARATOR = LINE_SEPARATOR << 16 | LINE_SEPARATOR; 						// /r/n/r/n
	private final static Pattern SRC_PATTERN = Pattern.compile("<img[\\w\\s=\"]*src=\\\"(.+?)\\\".*?>");
	private final static Pattern SRC_AD_PATTERN = Pattern.compile("src=\"ad\\d*\\..*?\"");
	private final static String[] TEXT_RESPONSE_TYPES = {"html", "text", "json", "xml"};
	
	
	public HttpClient(String host, HttpCommand command, int port) {
		socket = new Socket();
		try {
			//if(url.endsWith("/")) url=url.substring(0, url.length()-1);
			this.host = host;
			this.port = port;
			this.command = command;
			this.socket = new Socket(this.host, this.port);
			this.httpPrintWriter = new PrintWriter(this.socket.getOutputStream(), true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void setOutputPath(File outputPath) {
		this.outputPath = outputPath;
	}
	
	// Working principle of sending http requests:
	// sendRequest(..) is called externally, the request type is already set
	// sendRequest calls sendGetRequest(..)/sendPostRequest(..)/... , where the right headers and data are constructed (raw request)
	// sendGetRequest(..)/sendPostRequest(..)/... all call sendRawRequest(..) with the string rawRequest as argument
	public void sendRequest(String path, String file, String body) {
		switch(this.command) {
			case GET:
				sendGetRequest(path, file);
				break;
			case HEAD:
				sendHeadRequest(path, file);
				break;
			case POST:
				sendPostRequest(path, file, body);
				break;
			case PUT:
				sendPutRequest(path, file, body);
				break;
			default:
				break;
		}
	}
	
	private String constructGetRequest(String path, String file) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(String.format("%s %s HTTP/1.1", command.getCommandString(), path+file));
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(String.format("Host: %s:%d", this.host, this.port));
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		return sBuilder.toString();
	}
	
	private void sendGetRequest(String path, String file) {
		String request = constructGetRequest(path, file);
		sendRawHttpRequest(request, file);
		String[] sources = startListening();
		
		if(sources == null) return;
		
		for(String src:sources) {
			request = constructGetRequest(path, src);
			sendRawHttpRequest(request, src);
			startListening();
		}
	}
	private void sendHeadRequest(String path, String file) {
		// Head request is identical to get request, the difference is that the server only returns a http header
		// instead of a header and data
		sendRawHttpRequest(constructGetRequest(path, file), null);
		startListening();
	}
	
	private void sendPostRequest(String path, String file, String body) {
		if(file.equals("") && path.endsWith("/")) path = path.substring(0, path.length()-1);
		
		int bodyLength = body.length();
		String header = constructHeaderString(new String[][] {
			{"content-length", String.valueOf(bodyLength)},
			{"content-type", "application/x-www-form-urlencoded"}
		});
		
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(String.format("%s %s HTTP/1.1", command.getCommandString(), path+file));
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(String.format("Host: %s:%d", this.host, this.port));
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(header);
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(body);
		
		sendRawHttpRequest(sBuilder.toString(), null);
		startListening();
	}
	
	private void sendPutRequest(String path, String file, String body) {
		if(file.equals("") && path.endsWith("/")) path = path.substring(0, path.length()-1);
		int bodyLength = body.length();
		String header = constructHeaderString(new String[][] {{"content-length", String.valueOf(bodyLength)}});
		
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(String.format("%s %s HTTP/1.1", command.getCommandString(), path+file));
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(String.format("Host: %s:%d", this.host, this.port));
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(header);
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(body);
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		
		sendRawHttpRequest(sBuilder.toString(), null);
		startListening();
	}
	
	private void sendRawHttpRequest(String rawRequest, String file) {
		System.out.println(String.format("[REQUEST] %s",rawRequest.split("\n")[0]));
		String requestString = rawRequest;		
		setOutputFile(file);
		httpPrintWriter.print(requestString);
		httpPrintWriter.flush();
	}
	
	
	private void setOutputFile(String fileName) {
		if(outputPath == null || fileName == null) {
			outputFile=null;
			fileOutputStream=null;
			return;
		}
		
		if(fileName.equals("")) fileName = "index.html";
		String outputFilePath = outputPath.getPath() + "/" + fileName;
		outputFile = new File(outputFilePath);
		if(!outputFile.getParentFile().exists()) {
			outputFile.getParentFile().mkdirs();
		}
		
		try {
			fileOutputStream = new FileOutputStream(outputFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	
	/**
	 * Listen to the response of the sent HTTP request.
	 * If the received data is an html page ('content-type' contains 'html'),
	 * a string array filled with the values of src tags is returned.
	 * If the received data is not an html page, an empty string array is returned.
	 * The data is stored locally in the path outputPath.
	 */
	private String[] startListening() {
		try {
			InputStream inputStream = socket.getInputStream();
			StringBuilder headerBuilder = new StringBuilder();
			int c;
			int lastFourBytes=0;
			ByteBuffer byteBuffer = new ByteBuffer(this.inputBufferLength);
			String rawHeader="";
			
			// READ HTTP HEADER
			while ((c = inputStream.read()) != -1) {				// i between 0 ant 255
				if(byteBuffer.isFull()) {
					headerBuilder.append(new String(byteBuffer.getBuffer(), "UTF-8"));
					byteBuffer.reset();
				}
				byteBuffer.addByte((byte)c);
				
				lastFourBytes = (lastFourBytes << 8) | (0xff & c);	// 0xff & for extra safety, normally redundant bc. i between 0 and 255
				if(lastFourBytes == HttpClient.HEADER_SEPARATOR) {
					// end of header reached
					headerBuilder.append(new String(byteBuffer.getBuffer()),0, byteBuffer.getElementPointer()-4);
					rawHeader = headerBuilder.toString();
					// System.out.println("--> IN ---\n" + rawHeader+"\n--- END IN ---");
					
					break;
				}				
			}
			byteBuffer.reset();
			HttpResponseHeader header = new HttpResponseHeader(rawHeader);
			header.parse();
			
			System.out.println(String.format("[RESPONSE] Statuscode '%d %s'", header.getHttpStatusCode(), header.getHttpStatusString()));
			if(header.getHttpStatusCode() == 404) {
				System.out.println("[INFO] Aborting");
				return null;
			}
			
			if(this.command == HttpCommand.HEAD) {
				System.out.println("[RESPONSE] Headers received from HEAD command:");
				for(Entry<String, String> e : header.getEntries()) {
					System.out.println(String.format("%s : %s", e.getKey(), e.getValue()));
				}
				return null; // no response is expected
			}
			// Header is now read and parsed

			
			
			// READ HTTP DATA
			// To know how many bytes to read, there are 2 options:
			// Header contains content-length and number of bytes to be read OR header contains transfer-encoding: chunked
			// In case of the chunked encoding: each chunk is preceded by the length of the chunk
			// After the last chunk, a chunk with length 0 and \r\n\r\n are added
			String html = "";
			String contentType = header.getFieldValue("content-type");
			boolean isHtml = false;
			boolean isText = isTextualResponse(contentType);
			isHtml = (contentType != null && contentType.contains("html"));
			
			ResponseContentLengthType lengthType = determineResponseContentLengthType(header);
			if(lengthType == ResponseContentLengthType.FIXED) {
				int length = header.getContentLength();
				html = readFixedLengthResponse(inputStream, length, fileOutputStream, isText);
			}else if(lengthType == ResponseContentLengthType.CHUNKED) {
				html = readChunkedResponse(inputStream, fileOutputStream, isText);
			}else {
				throw new RuntimeException("Neither the content-length nor transfer-encoding:chunked is present in the HTTP header: no way to determine data length.");
			}
			
			if(isText) {
				System.out.println(String.format("[RESPONSE] Data with content-type: '%s':", contentType));
				System.out.println(html);
			} else {
				System.out.println(String.format("[RESPONSE] Data is not text but: content-type: '%s'" , contentType));
			}
			
			if(isHtml) {
				return findSources(html, true);
			}
			
			
		} catch(IOException e) {
        	e.printStackTrace();
        }
		
		return new String[] {};
	}
	
	/**
	 * 
	 * Determine if the given response is chunked (with header transfer-encoding:chunked) or fixed length (with header content-length: ... )
	 */
	private ResponseContentLengthType determineResponseContentLengthType(HttpResponseHeader header) {
		if(header.isFieldPresent("content-length")) {
			return ResponseContentLengthType.FIXED;
		}
		
		String transferEncodingValue = header.getFieldValue("transfer-encoding");
		if (transferEncodingValue == null) return null;
		return transferEncodingValue.equals("chunked") ? ResponseContentLengthType.CHUNKED : null;
	}
	
	/**
	 * Reads a chunked inputstream and writes the result to the FileOutputStream.
	 * If the read data has to be returned as a string, the argument returnAsString has to be set to true.
	 * Else, null is returned.
	 */
	private String readChunkedResponse(InputStream inputStream, FileOutputStream fos, boolean returnAsString) {
		ByteBuffer byteBuffer = new ByteBuffer(this.inputBufferLength);
		final String HEX_PREFIX = "0x";
		StringBuilder sBuilder = new StringBuilder();
		
		boolean firstChunk = true;
		// if the chunk is not the first, the chunk length is of the format 0x0d0a length 0x0d0a
		// if the chunk is the first one, the chunk length is of the format length 0x0d0a
		
		
		try {
			allChunksReadLoop : while(true) {
				boolean prefixLineSeparatorSkipped = false;
				// first: read length of next chunk followed by 0x0d0a
				// if the length is 0 followed by 0x0d0a0d0a, the end of the stream has been reached
				String hexStringChunkLength = "";
				short lastTwoBytes = 0; //short capacity: 2 bytes
				int readByte;
				chunkLengthLoop : while((readByte = inputStream.read()) != -1) {
					hexStringChunkLength += (char) readByte;
					
					lastTwoBytes = (short) (lastTwoBytes << 8 | readByte);
					
					if(lastTwoBytes == LINE_SEPARATOR) { // if last two bytes equals the line seperator, the end of the length encoding is reached
						if(firstChunk) {
							firstChunk = false;
							break chunkLengthLoop;
						} else {
							if(!prefixLineSeparatorSkipped) {
								// see declaration of firstChunk
								lastTwoBytes = 0;
								hexStringChunkLength = "";
								prefixLineSeparatorSkipped = true;
							}else {
								break chunkLengthLoop;
							}
						}
					}
				}
				hexStringChunkLength = hexStringChunkLength.substring(0, hexStringChunkLength.length()-2);
				int chunkLength = Integer.decode(HEX_PREFIX + hexStringChunkLength);
				if(chunkLength == 0) {
					break allChunksReadLoop;
				}
				
				// the length of the next data chunk is now determined
				// now that number of bytes has to be read from the input stream and afterwards, start over
				
				byteBuffer.reset();
				int totalBytesRead = 0;
				int byteRead = 0;
				chunkReadLoop: while ((byteRead = inputStream.read()) != -1) {				// i between 0 ant 255
					if(byteBuffer.isFull()) {
						if(returnAsString)sBuilder.append(new String(byteBuffer.getBuffer(),"UTF-8"));
						if(fileOutputStream != null) {
							fileOutputStream.write(byteBuffer.getBuffer(), 0, byteBuffer.getSize());
							fileOutputStream.flush();
						}
						byteBuffer.reset();
					}
					
					byteBuffer.addByte((byte) byteRead);
					totalBytesRead ++;
					
					if(totalBytesRead >= chunkLength) {
						break chunkReadLoop;
					} 
				}
				
				if(returnAsString)sBuilder.append(new String(byteBuffer.getBuffer(),"UTF-8"));
				if(fileOutputStream != null) {
					fileOutputStream.write(byteBuffer.getBuffer(), 0, byteBuffer.getElementPointer());
					fileOutputStream.flush();
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}

		return returnAsString ? sBuilder.toString() : null;
		
	}
	
	/**
	 * 
	 * Reads the data from an http response where the header content-length is present.
	 * The number of bytes specified in content-length is read from the given inputStream.
	 * The result is written to the given fileOutputStream.
	 * If the read data has to be returned as a string, the argument returnAsString has to be set to true.
	 * Else, null is returned.
	 * 
	 * @param inputStream
	 * @param length
	 * @param fos
	 * @param returnAsString
	 * @return
	 */
	private String readFixedLengthResponse(InputStream inputStream, int length, FileOutputStream fos, boolean returnAsString) {
		StringBuilder sBuilder = new StringBuilder();
		ByteBuffer byteBuffer = new ByteBuffer(this.inputBufferLength);
		int totalBytesRead = 0, bytesRead = 0;
		
		try {
			while ((bytesRead = inputStream.read(byteBuffer.getBuffer())) != -1) {				// i between 0 ant 255
				if(returnAsString) sBuilder.append(new String(byteBuffer.getBuffer(), "UTF-8").toCharArray(), 0, bytesRead);
				if(fileOutputStream != null) {
					fileOutputStream.write(byteBuffer.getBuffer(), 0, bytesRead);
					fileOutputStream.flush();
				}
				byteBuffer.reset();
				
				totalBytesRead += bytesRead;
				
				if(totalBytesRead >= length) {
					break;
				} 
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
		
		return returnAsString ? sBuilder.toString() : null;
	}
	
	
	
	/**
	 * 
	 * Scans the HTML page for src attributes and returns the values of those attributes as a String array.<br>
	 * Includes the possibility to block ads. Ads are where the value of a source attribute contains:<br>
	 * 'src="ad' followed by at least 0 digits, followed by a '.' followed by any extension, followed by a closing '"'<br>
	 * Ex. src="ad1.jpg", src="ad.new.png"
	 */
	public static String[] findSources(String htmlPage, boolean blockAds) {
		Matcher srcMatcher = HttpClient.SRC_PATTERN.matcher(htmlPage.toLowerCase());
		Matcher adMatcher = HttpClient.SRC_AD_PATTERN.matcher("");
		
		List<String> sources = new ArrayList<String>();
		
		
		while(srcMatcher.find()) {											// while the htmlPage still has src attributes left
			String wholeSrc = srcMatcher.group();							// get the whole src attribute string: ex. src="planet.jpg" (to later check against the admatcher)
			String src = srcMatcher.group(1);								// get the inside of the src attribute: ex. planet.jpg (to later add to the list of sources)		
			
			if(blockAds && adMatcher.reset(wholeSrc).find()) {				// if ads should be blocked and the src attribute string matches against the ad pattern
				System.out.println("[INFO] Detected ad: " + src);
				continue;													// don't add the inside of the attribute to the list
			}
			
			if(src.toLowerCase().contains("http://") || src.toLowerCase().contains("https://")) {						// source on other website
				continue;
			}
			
			sources.add(src);
		}
		String[] result = sources.toArray(new String[0]);
		
		return result;
	}
	
	public void closeConnection() {
		this.httpPrintWriter.close();
		try {
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Socket getSocket() {
		return this.socket;
	}
	
	public String constructPostBody(String[][] postValues) {
		StringBuilder sBuilder = new StringBuilder("");
		
		for(String[] field : postValues) {
			if(field.length != 2) throw new RuntimeException("Invalid post field: no key value pair.");
				sBuilder.append(field[0]);
				sBuilder.append("=");
				sBuilder.append(field[1]);
				sBuilder.append("&");
		}
		
		String result = sBuilder.toString();
		return result.substring(0, result.length()-1);
	}
	
	private String constructHeaderString(String[][] headers) {
		StringBuilder sBuilder = new StringBuilder("");
		for(String[] headerField : headers) {
			if(headerField.length != 2) throw new RuntimeException("Invalid header field: no key value pair.");
			sBuilder.append(headerField[0]);
			sBuilder.append(":");
			sBuilder.append(headerField[1]);
			sBuilder.append(HttpClient.LINE_SEPARATOR_STRING);
		}
		return sBuilder.toString();
	}
	
	private boolean isTextualResponse(String contentType) {
		for(String type : HttpClient.TEXT_RESPONSE_TYPES) {
			if(contentType.contains(type)) {
				return true;
			}
		}
		
		return false;
	}
	
	private enum ResponseContentLengthType {FIXED, CHUNKED};
	
}