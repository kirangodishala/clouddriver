package com.netflix.spinnaker.clouddriver.helpers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

public class WriteToFile {

  public static void createTempFile(byte[] contents) {
    try {
      File tempFile = new File("/tmp/spinflow1.log");
      if (!tempFile.exists()) {
        tempFile.createNewFile();
      }
      FileOutputStream fileOutputStream = new FileOutputStream(tempFile, true);
      BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
      bufferedOutputStream.write(("\n" + LocalDateTime.now() + "  CLOUDDRIVER ").getBytes());
      bufferedOutputStream.write(contents);
      bufferedOutputStream.close();
      fileOutputStream.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void createTempFile(String contents) {
    createTempFile(contents.getBytes());
  }
}
