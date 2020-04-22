import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Not fully implemented yet
 */
public class CacheManager {
	private File cacheFile;
	private Map<String, String> cache = new HashMap<String, String>();
	
	public CacheManager(File cacheFile) {
		this.cacheFile = cacheFile;
		initCacheFile();
	}
	
	private void initCacheFile() {
		if(this.cacheFile.isDirectory()) {
			throw new RuntimeException("The given cachefile is a directory");
		}
		
		if(!this.cacheFile.getParentFile().exists()) {
			this.cacheFile.getParentFile().mkdirs();
		}
		
		if(!this.cacheFile.exists()) {
			try {
				this.cacheFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			parseCacheFile();
		}
	}
	
	private void parseCacheFile() {
		try {
			FileInputStream reader = new FileInputStream(this.cacheFile);
			ByteBuffer buffer = new ByteBuffer(4096);
			StringBuilder sBuilder = new StringBuilder("");
			int bytesRead = 0;
			while((bytesRead=reader.read(buffer.getBuffer())) > 0){
				sBuilder.append(new String(buffer.getBuffer(), 0, bytesRead));
			}
			reader.close();
			
			String result = sBuilder.toString();
			//CSV format: host+path+file, date last downloaded
			String[] lines = result.split("\n");
			for(String line: lines) {
				if(line.length() == 0) continue;
				String[] fields = line.split(",");
				if(fields.length != 2) {
					throw new RuntimeException("Corrupt cache file: not exactly 2 fields present.");
				}
				
				this.put(fields[0], fields[1]);
			}			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public boolean contains(String file) {
		return this.cache.containsKey(file.trim().toLowerCase());
	}
	
	public void put(String file, String dateTime) {
		this.cache.put(file.trim().toLowerCase(), dateTime.trim());
	}
	
	public void putNow(String file) {
		//example date: Tue, 05 Jul 2016 23:27:52 GMT
		SimpleDateFormat dateFormatGmt = new SimpleDateFormat("dd MM yyyy HH:mm:ss");
		dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		String timeStamp = dateFormatGmt.format(Calendar.getInstance().getTime());
		System.out.println(timeStamp);
	}
	
	public void flush() {
		try {
			FileOutputStream fos = new FileOutputStream(this.cacheFile);
			fos.write(this.toString().getBytes());
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	@Override
	public String toString() {
		StringBuilder sBuilder = new StringBuilder();
		for (Map.Entry<String, String> entry : this.cache.entrySet()) {
			sBuilder.append(entry.getKey());
			sBuilder.append(",");
			sBuilder.append(entry.getValue());
			sBuilder.append("\n");
		}
		return sBuilder.substring(0, sBuilder.length()-1);
	}
}