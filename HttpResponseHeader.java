import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpResponseHeader {
	
	private String rawHeader;
	
	private Map<String, String> headerFields = new HashMap<String, String>();
	private String httpVersion;
	

	private int httpStatusCode;
	private String httpStatusString;
	private boolean parsed = false;

	
	private final static Pattern STATUS_PATTERN = Pattern.compile("(HTTP\\/\\d\\.*\\d*) (\\d+) (.+)");
	private final static Pattern FIELD_PATTERN = Pattern.compile("(.+?):(.*)");
	
	public HttpResponseHeader(String rawHeader) {
		this.rawHeader = rawHeader;
	}
	
	public void parse() {
		this.rawHeader = trimLineSeparators(this.rawHeader);
		int firstLineIndex = this.rawHeader.indexOf("\r\n");
		if(firstLineIndex == -1) {
			throw new RuntimeException("Parsing invalid header: no line separator present.");
		}
		String firstLine = this.rawHeader.substring(0, firstLineIndex);				// first line of header is status code
		String fieldLines = this.rawHeader.substring(firstLineIndex + 1);
		Matcher statusMatcher = HttpResponseHeader.STATUS_PATTERN.matcher(firstLine);
		Matcher fieldMatcher = HttpResponseHeader.FIELD_PATTERN.matcher(fieldLines);
		
		if(statusMatcher.find()) {
			this.httpVersion = statusMatcher.group(1);
			this.httpStatusCode = Integer.valueOf(statusMatcher.group(2));
			this.httpStatusString = statusMatcher.group(3);
		}else {
			throw new RuntimeException("First line of http header does not match pattern");
		}
		
		while(fieldMatcher.find()) {
			headerFields.put(fieldMatcher.group(1).toLowerCase(), fieldMatcher.group(2).trim().toLowerCase());
		}		
		
		parsed = true;
	}
	
	
	public String getHttpVersion() {
		if(!parsed) throw new RuntimeException("Header not parsed yet.");
		return httpVersion;
	}

	public int getHttpStatusCode() {
		if(!parsed) throw new RuntimeException("Header not parsed yet.");
		return httpStatusCode;
	}

	public String getHttpStatusString() {
		if(!parsed) throw new RuntimeException("Header not parsed yet.");
		return httpStatusString;
	}

	public boolean isParsed() {
		return parsed;
	}
	
	public boolean isFieldPresent(String field) {
		return getFieldValue(field) != null;
	}
	
	/**
	 * Returns the fields value if it's present in the header, null otherwise.
	 * The field is case insensitive.
	 * 
	 * 
	 * @param field The field from which the value has to be returned
	 * @return Null if the field is not present, the fields value as string if the field is present.
	 */
	public String getFieldValue(String field) {
		return this.headerFields.get(field.toLowerCase());
	}
	
	/**
	 * 
	 * @return The value of the content-length field if it is present, -1 if it is not present.
	 */
	public int getContentLength(){
		if(!parsed) throw new RuntimeException("Header not parsed yet.");
		String contentLength = this.headerFields.get("content-length");
		return contentLength == null ? -1 : Integer.valueOf(contentLength);
	}
	
	private String trimLineSeparators(String a) {
		String cr = "\r", nl = "\n";
		String newText = new String(a);
		while(newText.startsWith(cr) || newText.startsWith(nl)) {
			newText = newText.substring(1);
		}
		while(newText.endsWith(cr) || newText.endsWith(nl)) {
			newText = newText.substring(0, newText.length() - 1);
		}
		return newText;
	}
	
	public Set<Entry<String, String>> getEntries(){
		return this.headerFields.entrySet();
	}
	
}