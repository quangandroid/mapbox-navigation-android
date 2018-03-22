package com.mapbox.services.android.navigation.ui.v5.voice.polly;

import android.os.AsyncTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.ResponseBody;

public class InstructionTask extends AsyncTask<ResponseBody, Void, File> {
  private static int instructionNamingInt = 1;
  private final String cacheDirectory;
  private final TaskListener taskListener;

  InstructionTask(String cacheDirectory, TaskListener taskListener) {
    this.cacheDirectory = cacheDirectory;
    this.taskListener = taskListener;
  }

  @Override
  protected File doInBackground(ResponseBody... responseBodies) {
    return saveAsFile(responseBodies[0]);
  }

  /**
   * Saves the file returned in the response body as a file in the cache directory
   *
   * @param responseBody containing file
   * @return resulting file, or null if there were any IO exceptions
   */
  private File saveAsFile(ResponseBody responseBody) {
    try {
      File file = new File(cacheDirectory + File.separator + instructionNamingInt++ + ".mp3");
      InputStream inputStream = null;
      OutputStream outputStream = null;

      try {
        inputStream = responseBody.byteStream();
        outputStream = new FileOutputStream(file);
        byte[] buffer = new byte[4096];
        int numOfBytes;

        while ((numOfBytes = inputStream.read(buffer)) != -1) { // -1 denotes end of file
          outputStream.write(buffer, 0, numOfBytes);
        }

        outputStream.flush();
        return file;

      } catch (IOException exception) {
        return null;

      } finally {
        if (inputStream != null) {
          inputStream.close();
        }

        if (outputStream != null) {
          outputStream.close();
        }
      }

    } catch (IOException exception) {
      taskListener.onError();
      return null;
    }
  }

  @Override
  protected void onPostExecute(File instructionFile) {
    taskListener.onFinished(instructionFile);
  }

  public interface TaskListener {
    void onFinished(File file);

    void onError();
  }
}
