/* LanguageTool, a natural language style checker 
 * Copyright (C) 2011 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package org.languagetool.openoffice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.sun.star.frame.XController;
import com.sun.star.frame.XModel;
import com.sun.star.lang.XComponent;
import com.sun.star.text.XTextDocument;
import com.sun.star.uno.UnoRuntime;

/**
 * Class to read and write LT-Office-Extension-Cache
 * @since 5.2
 * @author Fred Kruse
 */
public class CacheIO implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final String CACHEFILE_MAP = "LtCacheMap";
  private static final String CACHEFILE_PREFIX = "LtCache";
  private static final String CACHEFILE_EXTENSION = "lcz";
  private static final int MIN_PARAGRAPHS_TO_SAVE_CACHE = 30;
  private static final boolean DEBUG_MODE = OfficeTools.DEBUG_MODE_IO;
  
  private String documentPath;
  private AllCaches allCaches;
  
  CacheIO(XComponent xComponent) {
    documentPath = getDocumentPath(xComponent);
  }
  
  /** 
   * returns the text cursor (if any)
   * returns null if it fails
   */
  private static String getDocumentPath(XComponent xComponent) {
    try {
      XTextDocument curDoc = UnoRuntime.queryInterface(XTextDocument.class, xComponent);
      if (curDoc == null) {
        MessageHandler.printToLogFile("XTextDocument not found!");
        return null;
      }
      XController xController = curDoc.getCurrentController();
      if (xController == null) {
        MessageHandler.printToLogFile("XController not found!");
        return null;
      }
      XModel xModel = xController.getModel();
      if (xModel == null) {
        MessageHandler.printToLogFile("XModel not found!");
        return null;
      }
      String url = xModel.getURL();
      if (url == null || !url.startsWith("file://")) {
        if (url != null && !url.isEmpty()) {
          MessageHandler.printToLogFile("Not a file URL: " + (url == null ? "null" : url));
        }
        return null;
      }
      URI uri = new URI(url);
      return uri.getPath();
    } catch (Throwable t) {
      MessageHandler.printException(t);     // all Exceptions thrown by UnoRuntime.queryInterface are caught
      return null;           // Return null as method failed
    }
  }

  /**
   * get the path to the cache file
   * if create == true: a new file is created if the file does not exist
   * if create == false: null is returned if the file does not exist
   */
  private String getCachePath(boolean create) {
    if (documentPath == null) {
      MessageHandler.printToLogFile("getCachePath: documentPath == null!");
      return null;
    }
    File cacheDir = OfficeTools.getCacheDir();
    if (DEBUG_MODE) {
      MessageHandler.printToLogFile("cacheDir: " + cacheDir.getAbsolutePath());
    }
    CacheFile cacheFile = new CacheFile(cacheDir);
    String cacheFileName = cacheFile.getCacheFileName(documentPath, create);
    if (cacheFileName == null) {
      MessageHandler.printToLogFile("getCachePath: cacheFileName == null!");
      return null;
    }
    File cacheFilePath = new File(cacheDir, cacheFileName);
    if (!create) {
      cacheFile.cleanUp();
    }
    if (DEBUG_MODE) {
      MessageHandler.printToLogFile("cacheFilePath: " + cacheFilePath.getAbsolutePath());
    }
    return cacheFilePath.getAbsolutePath();
  }
  
  /**
   * save all caches (document cache, all result caches) to cache file
   */
  private void saveAllCaches(String cachePath) {
    try {
      GZIPOutputStream fileOut = new GZIPOutputStream(new FileOutputStream(cachePath));
      ObjectOutputStream out = new ObjectOutputStream(fileOut);
      out.writeObject(allCaches);
      out.close();
      fileOut.close();
      MessageHandler.printToLogFile("Caches saved to: " + cachePath);
      if (DEBUG_MODE) {
        printCacheInfo();
      }
    } catch (Throwable t) {
      MessageHandler.printException(t);     // all Exceptions thrown by UnoRuntime.queryInterface are caught
    }
  }
  
  /**
   * save all caches if the document exceeds the defined minimum of paragraphs
   */
  public void saveCaches(XComponent xComponent, DocumentCache docCache, ResultCache sentencesCache, List<ResultCache> paragraphsCache) {
    String cachePath = getCachePath(true);
    if (cachePath != null) {
      try {
        if (docCache.size() >= MIN_PARAGRAPHS_TO_SAVE_CACHE) {
          allCaches = new AllCaches(docCache, sentencesCache, paragraphsCache);
          saveAllCaches(cachePath);
        } else {
          File file = new File( cachePath );
          if (file.exists() && !file.isDirectory()) {
            file.delete();
          }
        }
      } catch (Throwable t) {
        MessageHandler.printException(t);     // all Exceptions thrown by UnoRuntime.queryInterface are caught
      }
    }
  }
  
  /**
   * read all caches (document cache, all result caches) from cache file if it exists
   */
  public boolean readAllCaches() {
    String cachePath = getCachePath(false);
    if (cachePath == null) {
      return false;
    }
    try {
      File file = new File( cachePath );
      if (file.exists() && !file.isDirectory()) {
        GZIPInputStream fileIn = new GZIPInputStream(new FileInputStream(file));
        ObjectInputStream in = new ObjectInputStream(fileIn);
        allCaches = (AllCaches) in.readObject();
        in.close();
        fileIn.close();
        MessageHandler.printToLogFile("Caches read from: " + cachePath);
        if (DEBUG_MODE) {
          printCacheInfo();
        }
        return true;
      }
    } catch (Throwable t) {
      MessageHandler.printException(t);     // all Exceptions thrown by UnoRuntime.queryInterface are caught
    }
    return false;
  }
  
  /**
   * get document cache
   */
  public DocumentCache getDocumentCache() {
    return allCaches.docCache;
  }
  
  /**
   * get sentences cache (results for check of sentences)
   */
  public ResultCache getSentencesCache() {
    return allCaches.sentencesCache;
  }
  
  /**
   * get paragraph caches (results for check of paragraphes)
   */
  public List<ResultCache> getParagraphsCache() {
    return allCaches.paragraphsCache;
  }
  
  /**
   * set all caches to null
   */
  public void resetAllCache() {
    allCaches = null;
  }
  
  /**
   * print debug information of caches to log file
   */
  private void printCacheInfo() {
    MessageHandler.printToLogFile("Document Cache: Number of paragraphs: " + allCaches.docCache.size());
    MessageHandler.printToLogFile("Sentences Cache: Number of paragraphs: " + allCaches.sentencesCache.getNumberOfParas() 
        + ", Number of matches: " + allCaches.sentencesCache.getNumberOfMatches());
    MessageHandler.printToLogFile("Paragraph Cache(0): Number of paragraphs: " + allCaches.paragraphsCache.get(0).getNumberOfParas() 
        + ", Number of matches: " + allCaches.paragraphsCache.get(0).getNumberOfMatches());
    MessageHandler.printToLogFile("Paragraph Cache(1): Number of paragraphs: " + allCaches.paragraphsCache.get(1).getNumberOfParas() 
        + ", Number of matches: " + allCaches.paragraphsCache.get(1).getNumberOfMatches());
    for (int n = 0; n < allCaches.docCache.size(); n++) {
      MessageHandler.printToLogFile("allCaches.docCache.getFlatParagraphLocale(" + n + "): " 
            + (allCaches.docCache.getFlatParagraphLocale(n) == null ? "null" : OfficeTools.localeToString(allCaches.docCache.getFlatParagraphLocale(n))));
    }
    if (allCaches.sentencesCache == null) {
      MessageHandler.printToLogFile("sentencesCache == null");
    } else {
      if (allCaches.sentencesCache.getNumberOfMatches() > 0) {
        for (int n = 0; n < allCaches.sentencesCache.getNumberOfParas(); n++) {
          if (allCaches.sentencesCache.getMatches(n) == null) {
            MessageHandler.printToLogFile("allCaches.sentencesCache.getMatches(" + n + ") == null");
          } else {
            if (allCaches.sentencesCache.getMatches(n).length > 0) {
              MessageHandler.printToLogFile("Paragraph " + n + " sentence match[0]: " 
                  + "nStart = " + allCaches.sentencesCache.getMatches(n)[0].nErrorStart 
                  + ", nLength = " + allCaches.sentencesCache.getMatches(n)[0].nErrorLength
                  + ", errorID = " 
                  + (allCaches.sentencesCache.getMatches(n)[0].aRuleIdentifier == null ? "null" : allCaches.sentencesCache.getMatches(n)[0].aRuleIdentifier));
            }
          }
        }
      }
    }
  }

  class AllCaches implements Serializable {

    private static final long serialVersionUID = 1L;

    DocumentCache docCache;                 //  cache of paragraphs
    ResultCache sentencesCache;             //  Cache for matches of sentences rules
    List<ResultCache> paragraphsCache;      //  Cache for matches of text rules
    
    AllCaches(DocumentCache docCache, ResultCache sentencesCache, List<ResultCache> paragraphsCache) {
      this.docCache = docCache;
      this.sentencesCache = sentencesCache;
      this.paragraphsCache = paragraphsCache;
    }
    
  }

  /**
   * Class to to handle the cache files
   * cache files are stored in the LT configuration directory subdirectory 'cache'
   * the paths of documents are mapped to cache files and stored in the cache map
   */
  class CacheFile implements Serializable {

    private static final long serialVersionUID = 1L;
    private CacheMap cacheMap;
    private File cacheMapFile;

    CacheFile() {
      this(OfficeTools.getCacheDir());
    }

    CacheFile(File cacheDir) {
      cacheMapFile = new File(cacheDir, CACHEFILE_MAP);
      if (cacheMapFile != null) {
        if (cacheMapFile.exists() && !cacheMapFile.isDirectory()) {
          read();
        } else {
          cacheMap = new CacheMap();
          if (DEBUG_MODE) {
            MessageHandler.printToLogFile("create cacheMap file");
          }
          write(cacheMap);
        }
      }
    }

    /**
     * read the cache map from file
     */
    public void read() {
      try {
        FileInputStream fileIn = new FileInputStream(cacheMapFile);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        cacheMap = (CacheMap) in.readObject();
        if (DEBUG_MODE) {
          MessageHandler.printToLogFile("read cacheMap file: size=" + cacheMap.size());
        }
        in.close();
        fileIn.close();
      } catch (Throwable t) {
        MessageHandler.printException(t);     // all Exceptions thrown by UnoRuntime.queryInterface are caught
      }
    }

    /**
     * write the cache map to file
     */
    public void write(CacheMap cacheMap) {
      try {
        FileOutputStream fileOut = new FileOutputStream(cacheMapFile);
        ObjectOutputStream out = new ObjectOutputStream(fileOut);
        if (DEBUG_MODE) {
          MessageHandler.printToLogFile("write cacheMap file: size=" + cacheMap.size());
        }
        out.writeObject(cacheMap);
        out.close();
        fileOut.close();
      } catch (Throwable t) {
        MessageHandler.printException(t);     // all Exceptions thrown by UnoRuntime.queryInterface are caught
      }
    }
    
    /**
     * get the cache file name for a given document path
     * if create == true: create a cache file if it not exists 
     */
   public String getCacheFileName(String docPath, boolean create) {
      int orgSize = cacheMap.size();
      String cacheFileName = cacheMap.getOrCreateCacheFile(docPath, create);
      if (cacheMap.size() != orgSize) {
        write(cacheMap);
      }
      return cacheFileName;
    }
    
   /**
    * remove unused files from cache directory
    */
    public void cleanUp() {
      CacheCleanUp cacheCleanUp = new CacheCleanUp(cacheMap);
      cacheCleanUp.start();
    }
    
    /**
     * Class to create and handle the cache map
     * the clean up process is run in a separate parallel thread
     */
    class CacheMap implements Serializable {
      private static final long serialVersionUID = 1L;
      private Map<String, String> cacheNames;     //  contains the mapping from document paths to cache file names
      
      CacheMap() {
        cacheNames = new HashMap<>();
      }
      
      CacheMap(CacheMap in) {
        cacheNames = new HashMap<>();
        cacheNames.putAll(in.getCacheNames());
      }

      /**
       * get the cache map that contains the mapping from document paths to cache file names 
       */
      private Map<String, String> getCacheNames() {
        return cacheNames;
      }

      /**
       * get the size of the cache map
       */
      public int size() {
        return cacheNames.keySet().size();
      }
      
      /**
       * return true if the map contains the cache file name
       */
      public boolean containsValue(String value) {
        return cacheNames.containsValue(value);
      }
      
      /**
       * get all document paths contained in the map
       */
      public Set<String> keySet() {
        return cacheNames.keySet();
      }
      
      /**
       * get the cache file name from a document path
       */
      public String get(String key) {
        return cacheNames.get(key);
      }
      
      /**
       * remove a document paths from the map (inclusive the mapped cache file name)
       */
      public String remove(String key) {
        return cacheNames.remove(key);
      }
      
      /**
       * get the cache file name for a document paths
       * if create == true:  create the file if not exist
       * if create == false: return null if not exist
       */
      public String getOrCreateCacheFile(String docPath, boolean create) {
        if (DEBUG_MODE) {
          MessageHandler.printToLogFile("getOrCreateCacheFile: docPath=" + docPath);
          for (String file : cacheNames.keySet()) {
            MessageHandler.printToLogFile("cacheNames: docPath=" + file + ", cache=" + cacheNames.get(file));
          }
        }
        if (cacheNames.containsKey(docPath)) {
          return cacheNames.get(docPath);
        }
        if (!create) {
          return null;
        }
        int i = 1;
        String cacheName = CACHEFILE_PREFIX + i + "." + CACHEFILE_EXTENSION;
        while (cacheNames.containsValue(cacheName)) {
          i++;
          cacheName = CACHEFILE_PREFIX + i + "." + CACHEFILE_EXTENSION;
        }
        cacheNames.put(docPath, cacheName);
        return cacheName;
      }
    }
    
    /**
     * class to clean up cache
     * delete cache files for not existent document paths
     * remove not existent document paths and cache files from map
     * the clean up process is ran in a separate thread 
     */
    class CacheCleanUp extends Thread implements Serializable {
      private static final long serialVersionUID = 1L;
      private CacheMap cacheMap;
      
      CacheCleanUp(CacheMap in) {
        cacheMap = new CacheMap(in);
      }
      
      /**
       * run clean up process
       */
      @Override
      public void run() {
        try {
          boolean mapChanged = false;
          File cacheDir = OfficeTools.getCacheDir();
          List<String> mapedDocs = new ArrayList<String>();
          for (String doc : cacheMap.keySet()) {
            mapedDocs.add(doc);
          }
          for (String doc : mapedDocs) {
            File docFile = new File(doc);
            File cacheFile = new File(cacheDir, cacheMap.get(doc));
            if (DEBUG_MODE) {
              MessageHandler.printToLogFile("CacheMap: docPath=" + doc + ", docFile exist: " + (docFile == null ? "null" : docFile.exists()) + 
                  ", cacheFile exist: " + (cacheFile == null ? "null" : cacheFile.exists()));
            }
            if (docFile == null || !docFile.exists() || cacheFile == null || !cacheFile.exists()) {
              cacheMap.remove(doc);
              mapChanged = true;
              MessageHandler.printToLogFile("Remove Path from CacheMap: " + doc);
              if (cacheFile != null && cacheFile.exists()) {
                cacheFile.delete();
                MessageHandler.printToLogFile("Delete cache file: " + cacheFile.getAbsolutePath());
              }
            }
          }
          if (mapChanged) {
            if (DEBUG_MODE) {
              MessageHandler.printToLogFile("CacheCleanUp: Write CacheMap");
            }
            write(cacheMap);
          }
          File[] cacheFiles = cacheDir.listFiles();
          if (cacheFiles != null) {
            for (File cacheFile : cacheFiles) {
              if (!cacheMap.containsValue(cacheFile.getName()) && !cacheFile.getName().equals(CACHEFILE_MAP)) {
                cacheFile.delete();
                MessageHandler.printToLogFile("Delete cache file: " + cacheFile.getAbsolutePath());
              }
            }
          }
        } catch (Throwable t) {
          MessageHandler.printException(t);     // all Exceptions thrown by UnoRuntime.queryInterface are caught
        }
      }
    }
  }
  
}
