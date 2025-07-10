package tqmc.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSInputStream;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import prerna.cluster.util.ClusterUtil;
import prerna.engine.api.IStorageEngine;
import prerna.om.Insight;
import prerna.om.InsightFile;
import prerna.project.api.IProject;
import prerna.util.AssetUtility;
import prerna.util.Constants;
import prerna.util.Utility;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.RecordFile;
import tqmc.domain.base.TQMCException;

/*

Upload:
File uploaded to InsightCache/<session>/<insight>/<path>
Encrypt to InsightCache/<session>/<insight>/<caseType>/<recordId>/<recordFileId>/data
Upload encrypted version to <dest>/<caseType>/<recordId>/<recordFileId>/data
Delete touched files from InsightCache

Download:
Download to InsightCache/<session>/<insight>/<caseType>/<recordId>/<recordFileId>/data
Decrypt and Encrypt to InsightCache/<session>/<insight>/<fileNameUniq>
Delete original download

Delete:
Delete from insight cache
Delete from storage

*/

public class FileUtils {
  private static final Logger LOGGER = LogManager.getLogger(FileUtils.class);

  private static final String DEFAULT_FILE_NAME = "data";

  // Pattern that checks if the path has invalid characters
  private static final Pattern INVALID_WINDOWS_CHARS = Pattern.compile("[<>:\"/\\\\|?*]");

  // If this is a local project, this will be true. If not, this is false. This determines the
  // method used to save files.
  private final boolean isProject;

  private final String projectId;
  private final IProject project;

  private final String storageId;
  private final IStorageEngine storage;

  private final String storagePass;

  /**
   * This concurrent hash map stores all files that are being modified currently and prevents
   * simultaneous modification of the file in question. Keys are the path to the download file in
   * insightClasses
   */
  private static final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

  public void lock(String key) {
    ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());
    lock.lock();
  }

  public void unlock(String key) {
    ReentrantLock lock = lockMap.get(key);
    if (lock != null) {
      lock.unlock();
      /**
       * Safe cleanup on the following conditions: 1. Nobody holds the lock 2. Nobody is trying to
       * acquire it 3. Nobody will insert the lock again between the test and remove
       */
      if (!lock.hasQueuedThreads() && !lock.isLocked()) {
        lockMap.computeIfPresent(
            key, (k, v) -> (v == lock && !lock.hasQueuedThreads() && !lock.isLocked()) ? null : v);
      }
    }
  }

  public FileUtils(String projectId) {
    TQMCProperties props = TQMCProperties.getInstance(projectId);

    this.projectId = projectId;
    project = Utility.getProject(projectId);

    isProject = props.getStorageId() == null || props.getStorageId().isEmpty();
    if (isProject) {
      storageId = projectId;
      storage = null;
    } else {
      storageId = props.getStorageId();
      storage = Utility.getStorage(storageId);
    }

    storagePass = StringUtils.trimToEmpty(project.getSmssProp().getProperty("secretKey"));
  }

  private String getRecordPath(String productLine, String recordId) {
    return "tqmc_files/" + productLine + "/" + recordId;
  }

  public void uploadFilesToRecord(
      String insightFolder, String caseType, String recordId, List<RecordFile> recordFiles) {
    String path = getRecordPath(caseType, recordId);
    if (isProject) {
      uploadFilesToProject(insightFolder, path, recordFiles);
    } else {
      uploadFilesToStorage(insightFolder, path, recordFiles);
    }
  }

  public void deleteFilesFromRecord(
      Insight insight, String caseType, String recordId, List<RecordFile> recordFiles) {
    String path = getRecordPath(caseType, recordId);
    if (isProject) {
      deleteFilesInPathFromProject(path, recordFiles);
    } else {
      deleteFilesInPathFromStorage(path, recordFiles);
    }
    for (RecordFile recordFile : recordFiles) {
      insight.getExportInsightFiles().remove(recordFile.getRecordFileId());
    }
  }

  private String downloadFileFromProject(
      String storagePath,
      String destinationDirectory,
      String destinationFileName,
      String destinationPass) {
    File sourceFile =
        Paths.get(AssetUtility.getProjectAppRootFolder(projectId), storagePath, DEFAULT_FILE_NAME)
            .toFile();
    File downloadFile = Paths.get(destinationDirectory, storagePath, DEFAULT_FILE_NAME).toFile();
    String encryptDownloadPath = null;
    String lockKey = Paths.get(destinationDirectory, storagePath).toString();

    this.lock(lockKey);
    try {
      if (!downloadFile.exists()) {
        org.apache.commons.io.FileUtils.copyFile(sourceFile, downloadFile);
      }
      encryptDownloadPath =
          encryptDownload(downloadFile, destinationDirectory, destinationFileName, destinationPass);
    } catch (Exception e) {
      LOGGER.warn("Failed to create file at path " + downloadFile.toString(), e);
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Error occurred trying to download file", e);
    } finally {
      this.unlock(lockKey);
      if (encryptDownloadPath == null) {
        throw new TQMCException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Error occurred trying to download file");
      }
    }

    return encryptDownloadPath;
  }

  private String downloadFileFromStorage(
      String storagePath,
      String destinationDirectory,
      String destinationFileName,
      String destinationPass) {
    Path downloadPath = Paths.get(destinationDirectory, storagePath);
    File downloadFile = null;
    String encryptDownloadPath = null;
    String lockKey = downloadPath.toString();

    this.lock(lockKey);
    try {
      downloadPath.toFile().mkdirs();
      storage.copyToLocal(storagePath, downloadPath.toString());
      downloadFile = downloadPath.resolve(DEFAULT_FILE_NAME).toFile();
      encryptDownloadPath =
          encryptDownload(downloadFile, destinationDirectory, destinationFileName, destinationPass);
    } catch (Exception e) {
      LOGGER.warn("Failed to create file at path " + downloadPath.toString(), e);
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Error occurred trying to download file", e);
    } finally {
      this.unlock(lockKey);
    }

    return encryptDownloadPath;
  }

  private File getDestinationFile(String destinationDirectory, String destinationFileName) {
    return Paths.get(destinationDirectory, destinationFileName).normalize().toFile();
  }

  private String encryptDownload(
      File downloadFile,
      String destinationDirectory,
      String destinationFileName,
      String destinationPass)
      throws IllegalArgumentException {

    File targetFile = getDestinationFile(destinationDirectory, destinationFileName);

    if (targetFile.exists()) {
      //    	IMPORTANT: So that we don't download the file twice to the backend.
      return targetFile.getAbsolutePath();
    }

    targetFile = getUniqueDestinationFile(destinationDirectory, destinationFileName);
    encrypt(downloadFile, targetFile, storagePass, destinationPass);

    try {
      downloadFile.delete();
    } catch (SecurityException e) {
      LOGGER.warn("Failed to delete file at path " + downloadFile.toString(), e);
    }
    return targetFile.getAbsolutePath();
  }

  private void encrypt(File sourceFile, File targetFile, String oldPass, String newPass) {
    // Assume sourceFile is always a PDF at this point

    String sourceFilePath = sourceFile.getAbsolutePath();
    String decryptedFilePath = FilenameUtils.removeExtension(sourceFilePath) + "-decrypted.pdf";

    try (PDDocument doc =
        Loader.loadPDF(new RandomAccessReadBufferedFile(new File(sourceFilePath)), oldPass)) {

      doc.setAllSecurityToBeRemoved(true);
      doc.save(decryptedFilePath);
    } catch (Exception e) {
      LOGGER.error(Constants.STACKTRACE, e);
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Failed to decrypt file: " + e.getMessage());
    }

    try (PDDocument doc =
        Loader.loadPDF(new RandomAccessReadBufferedFile(new File(decryptedFilePath)), "")) {
      int keyLength = 256;

      AccessPermission ap = new AccessPermission();
      ap.setCanPrint(false);
      ap.setCanExtractContent(false);
      ap.setCanModify(false);
      ap.setCanModifyAnnotations(false);
      ap.setCanAssembleDocument(false);
      ap.setReadOnly();

      String randomOwner = UUID.randomUUID().toString();
      StandardProtectionPolicy spp = new StandardProtectionPolicy(randomOwner, newPass, ap);
      spp.setEncryptionKeyLength(keyLength);

      doc.protect(spp);
      doc.save(targetFile);
    } catch (IOException e) {
      LOGGER.error(Constants.STACKTRACE, e);
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Failed to recrypt file: " + e.getMessage());
    } finally {
      if (decryptedFilePath != null) {
        File decryptedFile = new File(decryptedFilePath);
        if (decryptedFile.exists() && !decryptedFile.delete()) {
          throw new TQMCException(
              ErrorCode.INTERNAL_SERVER_ERROR,
              "Failed to delete temporary file: " + decryptedFilePath);
        }
        LOGGER.info("Deleted temporary file: " + decryptedFilePath);
      }
    }
  }

  private void addImageToPDF(PDDocument pdfDoc, BufferedImage image) throws IOException {
    PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
    pdfDoc.addPage(page);

    PDImageXObject pdImage = LosslessFactory.createFromImage(pdfDoc, image);
    PDPageContentStream contentStream = new PDPageContentStream(pdfDoc, page);
    contentStream.drawImage(pdImage, 0, 0, image.getWidth(), image.getHeight());
    contentStream.close();
  }

  private File getUniqueDestinationFile(String destinationDirectory, String destinationFileName)
      throws IllegalArgumentException {
    Path targetPath = Paths.get(destinationDirectory, destinationFileName).normalize();
    String targetPathString = targetPath.toString();
    String targetFileName = FilenameUtils.getBaseName(targetPathString).trim();
    String targetFileExtension = FilenameUtils.getExtension(targetPathString).trim();

    File targetFile = targetPath.toFile();
    int counter = 1;
    while (targetFile.exists()) {
      targetFile =
          Paths.get(
                  destinationDirectory,
                  targetFileName + " (" + counter + ")." + targetFileExtension)
              .toFile();
      counter++;
    }
    return targetFile;
  }

  private static void appendAllEmbeddedPDFs(
      Map<String, PDComplexFileSpecification> files, Path combinedPdfPath, String fileName)
      throws IOException {

    List<PDDocument> opened = new ArrayList<>(); // keep them alive
    try (PDDocument masterDoc = new PDDocument()) {

      for (PDComplexFileSpecification spec : files.values()) {
        PDEmbeddedFile ef = spec.getEmbeddedFile();
        if (ef == null || !"application/pdf".equalsIgnoreCase(ef.getSubtype())) {
          LOGGER.warn("Embedded file was null or skipped bc not a pdf");
          continue; // skip non PDFs
        }

        COSInputStream is = ef.createInputStream(); // will be closed with doc
        RandomAccessRead rar = new RandomAccessReadBuffer(is);
        PDDocument embedded = Loader.loadPDF(rar); // don�t close yet
        opened.add(embedded);

        for (PDPage page : embedded.getPages()) {
          masterDoc.importPage(page); // still references embedded
        }
      }

      masterDoc.save(combinedPdfPath.toFile()); // works, sources still open
    } finally {

      if (opened.isEmpty()) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid PDF Binder: " + fileName);
      }

      for (PDDocument d : opened) { // now we can close them
        IOUtils.closeQuietly(d);
      }
    }
  }

  private void uploadFilesToProject(
      String insightFolder, String path, List<RecordFile> recordFiles) {
    Path destinationDirectoryPath =
        Paths.get(AssetUtility.getProjectAppRootFolder(projectId), path);
    String destinationDirectoryString = destinationDirectoryPath.toString();

    recordFiles
        .parallelStream()
        .forEach(
            recordFile -> {
              try {
                //      for (RecordFile recordFile : recordFiles) {
                String fileName = recordFile.getFileName();
                if (INVALID_WINDOWS_CHARS.matcher(fileName).find()) {
                  fileName = INVALID_WINDOWS_CHARS.matcher(fileName).replaceAll("");
                }

                File uploadFile = Paths.get(insightFolder, fileName).toFile();

                File pdfFile = convertToPDFIfNeeded(uploadFile);

                File targetFile =
                    Paths.get(
                            destinationDirectoryString,
                            recordFile.getRecordFileId(),
                            DEFAULT_FILE_NAME)
                        .toFile();

                org.apache.commons.io.FileUtils.createParentDirectories(targetFile);
                encrypt(pdfFile, targetFile, "", storagePass);
                try {
                  uploadFile.delete();
                  if (pdfFile.exists()) {
                    pdfFile.delete();
                  }
                } catch (SecurityException e) {
                  LOGGER.warn("Failed to delete file at path " + uploadFile.toString(), e);
                }
                //      }

              } catch (InvalidPathException e) {
                throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid file name or path", e);
              } catch (Exception e) {
                LOGGER.warn("Failed to create files at path " + path, e);
                throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage(), e);
              }
            });

    if (ClusterUtil.IS_CLUSTER) {
      IProject project = Utility.getProject(projectId);
      ClusterUtil.pushProjectFolder(project, destinationDirectoryString);
    }
  }

  private void uploadFilesToStorage(
      String insightFolder, String path, List<RecordFile> recordFiles) {
    // Use thread-safe lists
    List<File> uploadedFiles = java.util.Collections.synchronizedList(new ArrayList<>());
    List<File> encryptedFiles = java.util.Collections.synchronizedList(new ArrayList<>());

    recordFiles
        .parallelStream()
        .forEach(
            recordFile -> {
              try {
                String fileName = recordFile.getFileName();
                if (INVALID_WINDOWS_CHARS.matcher(fileName).find()) {
                  fileName = INVALID_WINDOWS_CHARS.matcher(fileName).replaceAll("");
                }

                File uploadFile = Paths.get(insightFolder, fileName).toFile();
                File pdfFile = convertToPDFIfNeeded(uploadFile);

                File targetFile =
                    Paths.get(insightFolder, path, recordFile.getRecordFileId(), DEFAULT_FILE_NAME)
                        .toFile();
                org.apache.commons.io.FileUtils.createParentDirectories(targetFile);
                encrypt(pdfFile, targetFile, "", storagePass);

                uploadedFiles.add(uploadFile);
                encryptedFiles.add(targetFile);

                if (!uploadFile.equals(pdfFile)) {
                  if (pdfFile.exists() && !pdfFile.delete()) {
                    LOGGER.warn("Failed to delete temp file: " + pdfFile.getAbsolutePath());
                  }
                }
              } catch (InvalidPathException e) {
                throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid file name or path", e);
              } catch (Exception e) {
                LOGGER.warn("Failed to create files at path " + path, e);
                throw new TQMCException(
                    ErrorCode.INTERNAL_SERVER_ERROR, "Error occurred trying to upload files", e);
              }
            });

    try {
      storage.copyToStorage(Paths.get(insightFolder, path).toString(), path, null);
      for (File f : uploadedFiles) {
        try {
          f.delete();
        } catch (SecurityException e) {
          LOGGER.warn("Failed to delete file at path " + f.toString(), e);
        }
      }
    } catch (Exception e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    } finally {
      for (File f : encryptedFiles) {
        try {
          f.delete();
        } catch (SecurityException e) {
          LOGGER.warn("Failed to delete file at path " + f.toString(), e);
        }
      }
    }
  }

  private File convertToPDFIfNeeded(File sourceFile) {
    String extension = FilenameUtils.getExtension(sourceFile.getName()).toLowerCase();
    String fileName = sourceFile.toPath().getFileName().toString();
    String convertedPdfPath = null;

    switch (extension) {
      case "pdf":
        try (PDDocument doc = Loader.loadPDF(sourceFile)) {
          PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
          PDEmbeddedFilesNameTreeNode efTree = names.getEmbeddedFiles();
          if (efTree == null) {
            LOGGER.info("No EmbeddedFiles section found - safe to return PDF as is");
            return sourceFile;
          }

          convertedPdfPath =
              FilenameUtils.removeExtension(sourceFile.getAbsolutePath()) + "-as-pdf.pdf";

          Map<String, PDComplexFileSpecification> embedded = efTree.getNames();
          if (embedded != null) {
            appendAllEmbeddedPDFs(embedded, Paths.get(convertedPdfPath), fileName);
          } else {
            if (efTree.getKids() != null) {
              for (PDNameTreeNode<PDComplexFileSpecification> node : efTree.getKids()) {
                Map<String, PDComplexFileSpecification> kidNames = node.getNames();
                if (kidNames != null) {
                  appendAllEmbeddedPDFs(kidNames, Paths.get(convertedPdfPath), fileName);
                }
              }
            } else {
              throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid PDF Binder: " + fileName);
            }
          }
        } catch (IOException e) {
          throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
        break;
      case "tif":
      case "tiff":
        try {
          ImageInputStream iis = ImageIO.createImageInputStream(sourceFile);
          Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
          if (!readers.hasNext()) {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST, "Invalid " + extension.toUpperCase() + " file: " + fileName);
          }
          ImageReader reader = readers.next();
          reader.setInput(iis);

          int numPages = reader.getNumImages(true);

          PDDocument pdfDoc = new PDDocument();

          for (int i = 0; i < numPages; i++) {
            BufferedImage image = reader.read(i);
            addImageToPDF(pdfDoc, image);
          }

          convertedPdfPath =
              FilenameUtils.removeExtension(sourceFile.getAbsolutePath()) + "-as-pdf.pdf";
          pdfDoc.save(convertedPdfPath);
          pdfDoc.close();

          reader.dispose();
          iis.close();
        } catch (IOException e) {
          throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
        break;
      case "jpg":
      case "jpeg":
        try {
          PDDocument pdfDoc = new PDDocument();

          BufferedImage image = ImageIO.read(sourceFile);
          addImageToPDF(pdfDoc, image);

          convertedPdfPath =
              FilenameUtils.removeExtension(sourceFile.getAbsolutePath()) + "-as-pdf.pdf";
          pdfDoc.save(convertedPdfPath);
          pdfDoc.close();
        } catch (IOException e) {
          throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
        break;
      case "docx":
        /**
         * OPTION 1: Docx4J straight to PDF It is very straightforward but the resulting PDF is a
         * bit janky
         */

        //        try {
        //          WordprocessingMLPackage wordMLPackage =
        // WordprocessingMLPackage.load(sourceFile);
        //          convertedPdfPath = FilenameUtils.removeExtension(sourceFilePath) +
        // "-as-pdf.pdf";
        //          FileOutputStream fos = new FileOutputStream(convertedPdfPath);
        //          Docx4J.toPDF(wordMLPackage, fos);
        //
        //          fos.flush();
        //          fos.close();
        //          sourceFilePath = convertedPdfPath;
        //        } catch (Docx4JException | IOException e) {
        //          throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        //        }

        /**
         * OPTION 2: Docx4J to HTML and HTML to PDF Convert to HTML, then use toPDF reactor to
         * convert Downside - need to pass insight to file utils, may take a long time to refactor
         * Upside - if word to HTML is stable and HTML to PDF is stable, this could work well
         *
         * <p>One would have to pass the HTML as an encoded string to this reactor in the HTML
         * param. The other params are mostly not mandatory so should be alright
         */

        //        try {
        //        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(sourceFile);
        ////        MainDocumentPart mdp = wordMLPackage.getMainDocumentPart();
        //        convertedHtmlPath = FilenameUtils.removeExtension(sourceFilePath) +
        // "-as-html.pdf";
        //        FileOutputStream fos = new FileOutputStream(convertedHtmlPath);
        //        HTMLSettings htmlSettings = Docx4J.createHTMLSettings();
        //        htmlSettings.setOpcPackage(wordMLPackage);
        //        Docx4J.toHTML(htmlSettings, fos, Docx4J.FLAG_EXPORT_PREFER_XSL);
        //
        //        fos.flush();
        //        fos.close();
        //
        //        AbstractReactor toPdf = new ToPdfReactor();
        //
        ////        Add insight, nounstore args, execute reactor, handle errors, then save as PDF
        //
        ////        sourceFilePath = convertedHtmlPath;
        //      } catch (Docx4JException | IOException e) {
        //        // TODO Auto-generated catch block
        //        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        //      }
      default:
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Unexpected file format");
    }
    return new File(convertedPdfPath);
  }

  private void deleteFilesInPathFromProject(String path, List<RecordFile> recordFiles) {
    String projectPath = AssetUtility.getProjectAppRootFolder(projectId);

    recordFiles
        .parallelStream()
        .forEach(
            recordFile -> {
              File requestedFile =
                  Paths.get(projectPath, path, recordFile.getRecordFileId(), DEFAULT_FILE_NAME)
                      .toFile();
              if (!requestedFile.exists()) {
                return;
              }
              try {
                requestedFile.delete();
              } catch (SecurityException e) {
                LOGGER.warn("Failed to delete file at path " + requestedFile.toString(), e);
              }
            });

    if (ClusterUtil.IS_CLUSTER) {
      IProject project = Utility.getProject(storageId);
      ClusterUtil.pushProjectFolder(project, Paths.get(projectPath, path).toString());
    }
  }

  private void deleteFilesInPathFromStorage(String path, List<RecordFile> recordFiles) {
    recordFiles
        .parallelStream()
        .forEach(
            recordFile -> {
              String requestedPath = Paths.get(path, recordFile.getRecordFileId()).toString();
              try {
                storage.deleteFromStorage(requestedPath, true);
              } catch (Exception e) {
                LOGGER.warn("Failed to delete folder at path " + requestedPath, e);
                throw new TQMCException(
                    ErrorCode.INTERNAL_SERVER_ERROR, "Error occurred trying to delete files", e);
              }
            });
  }
}
