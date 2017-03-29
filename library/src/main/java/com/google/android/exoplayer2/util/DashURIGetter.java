package com.google.android.exoplayer2.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Map;
import java.util.TreeMap;

import android.util.Log;
import com.google.android.exoplayer2.util.Util;

/**
 * Represents youtube video information retriever.
 */
public class DashURIGetter
{
  private static final String TAG = "DashURIGetter";

  private static final String URL_YOUTUBE_GET_VIDEO_INFO = "http://www.youtube.com/get_video_info?&video_id=";

  public static final String KEY_DASH_VIDEO    = "dashmpd";
  public static final String KEY_HLS_VIDEO     = "hlsvp";
  public static final String KEY_ADAPTIVE_FMTS = "adaptive_fmts";

  private TreeMap<String, String> kvpList = new TreeMap<>();

  private String url;

  public void retrieve() throws IOException
  {
    boolean fromYTDT = url.contains(Util.YTPatternDT);
    boolean fromYTMobile = url.contains(Util.YTPatternMobile);

    String targetUrl;

    if (fromYTDT) {
      targetUrl = URL_YOUTUBE_GET_VIDEO_INFO + url.substring(Util.YTPatternDT.length());
    } else if (fromYTMobile) {
      targetUrl = URL_YOUTUBE_GET_VIDEO_INFO + url.substring(Util.YTPatternMobile.length());
    } else {
      return;
    }
    Log.d(TAG, "targetUrl = " + targetUrl);
    SimpleHttpClient client = new SimpleHttpClient();
    String response = client.execute(targetUrl, SimpleHttpClient.HTTP_GET, SimpleHttpClient.DEFAULT_TIMEOUT);
    parse(response);
  }

  public String getInfo(String key)
  {
    return kvpList.get(key);
  }

  private void parse(String data) throws UnsupportedEncodingException
  {
 
    String[] splits = data.split("&");
    String kvpStr = "";

    if(splits.length < 1)
    {
      return;
    }

    kvpList.clear();

    for(int i = 0; i < splits.length; ++i)
    {
      kvpStr = splits[i];
      Log.d(TAG, "kvpStr = " + kvpStr);
      try
      {
        // Data is encoded multiple times
        kvpStr = URLDecoder.decode(kvpStr, SimpleHttpClient.ENCODING_UTF_8);
        kvpStr = URLDecoder.decode(kvpStr, SimpleHttpClient.ENCODING_UTF_8);

        String[] kvpSplits = kvpStr.split("=", 2);

        if(kvpSplits.length == 2)
        {
          kvpList.put(kvpSplits[0], kvpSplits[1]);
        }
        else if(kvpSplits.length == 1)
        {
          kvpList.put(kvpSplits[0], "");
        }
      }
      catch (UnsupportedEncodingException ex)
      {
        throw ex;
      }
    }
  }

  public static class SimpleHttpClient
  {
    public static final String ENCODING_UTF_8 = "UTF-8";
    public static final int DEFAULT_TIMEOUT = 10000;

    public static final String HTTP_GET = "GET";

    public String execute(String urlStr, String httpMethod, int timeout) throws IOException
    {
      URL url = null;
      HttpURLConnection conn = null;
      InputStream inStream = null;
      OutputStream outStream = null;
      String response = null;
      boolean result = true; 
	  
      try
      {
        url = new URL(urlStr);
        conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(timeout);
        conn.setRequestMethod(httpMethod);

        inStream = new BufferedInputStream(conn.getInputStream());
        response = getInput(inStream);
      }
      finally
      {
        if(conn != null && conn.getErrorStream() != null)
        {
          String errorResponse = " : ";
          errorResponse = errorResponse + getInput(conn.getErrorStream());
          response = response + errorResponse;
          result = false;
        }

        if (conn != null)
        {
          conn.disconnect();
        }
      }

      return response;
    }

    private String getInput(InputStream in) throws IOException
    {
      StringBuilder sb = new StringBuilder(8192);
      byte[] b = new byte[1024];
      int bytesRead = 0;

      while (true)
      {
        bytesRead = in.read(b);
        if (bytesRead < 0)
        {
          break;
        }
        String s = new String(b, 0, bytesRead, ENCODING_UTF_8);
        sb.append(s);
      }

      return sb.toString();
    }

  }

  public DashURIGetter (String url) {
    this.url = url;
  } 
}
