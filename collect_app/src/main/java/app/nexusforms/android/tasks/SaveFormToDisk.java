/*
 * Copyright (C) 2009 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package app.nexusforms.android.tasks;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;

import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathNodeset;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import app.nexusforms.analytics.Analytics;
import app.nexusforms.android.R;

import app.nexusforms.android.analytics.AnalyticsEvents;
import app.nexusforms.android.application.Collect;
import app.nexusforms.android.database.DatabaseFormsRepository;
import app.nexusforms.android.database.DatabaseInstancesRepository;
import app.nexusforms.android.exception.EncryptionException;
import app.nexusforms.android.formentry.saving.FormSaver;
import app.nexusforms.android.instances.Instance;
import app.nexusforms.android.instances.InstancesRepository;
import app.nexusforms.android.javarosawrapper.FormController;
import app.nexusforms.android.provider.FormsProviderAPI;
import app.nexusforms.android.provider.InstanceProviderAPI;
import app.nexusforms.android.storage.StoragePathProvider;
import app.nexusforms.android.storage.StorageSubdirectory;
import app.nexusforms.android.utilities.EncryptionUtils;
import app.nexusforms.android.utilities.FileUtils;
import app.nexusforms.android.utilities.MediaUtils;
import app.nexusforms.android.utilities.TranslationHandler;
import app.nexusforms.android.forms.Form;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import timber.log.Timber;

/**
 * Background task for loading a form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SaveFormToDisk {

    private final boolean saveAndExit;
    private final boolean shouldFinalize;
    private final FormController formController;
    private final MediaUtils mediaUtils;
    private Uri uri;
    private String instanceName;
    private final Analytics analytics;
    private final ArrayList<String> tempFiles;

    public static final int SAVED = 500;
    public static final int SAVE_ERROR = 501;
    public static final int SAVED_AND_EXIT = 504;
    public static final int ENCRYPTION_ERROR = 505;

    public SaveFormToDisk(FormController formController, MediaUtils mediaUtils, boolean saveAndExit, boolean shouldFinalize, String updatedName,
                          Uri uri, Analytics analytics, ArrayList<String> tempFiles) {
        this.formController = formController;
        this.mediaUtils = mediaUtils;
        this.uri = uri;
        this.saveAndExit = saveAndExit;
        this.shouldFinalize = shouldFinalize;
        this.instanceName = updatedName;
        this.analytics = analytics;
        this.tempFiles = tempFiles;
    }

    @Nullable
    public SaveToDiskResult saveForm(FormSaver.ProgressListener progressListener) {
        SaveToDiskResult saveToDiskResult = new SaveToDiskResult();

        progressListener.onProgressUpdate(TranslationHandler.getString(Collect.getInstance(), R.string.survey_saving_validating_message));

        try {
            int validateStatus = formController.validateAnswers(shouldFinalize);
            if (validateStatus != FormEntryController.ANSWER_OK) {
                // validation failed, pass specific failure
                saveToDiskResult.setSaveResult(validateStatus, shouldFinalize);
                return saveToDiskResult;
            }
        } catch (Exception e) {
            Timber.e(e);
            saveToDiskResult.setSaveErrorMessage(e.getMessage());
            saveToDiskResult.setSaveResult(SAVE_ERROR, shouldFinalize);
            return saveToDiskResult;
        }

        if (shouldFinalize) {
            formController.postProcessInstance();
        }

        // close all open databases of external data.
        Collect.getInstance().getExternalDataManager().close();

        // if there is a meta/instanceName field, be sure we are using the latest value
        // just in case the validate somehow triggered an update.
        String updatedSaveName = formController.getSubmissionMetadata().instanceName;
        if (updatedSaveName != null) {
            instanceName = updatedSaveName;
        }

        try {
            exportData(shouldFinalize, progressListener);

            if (formController.getInstanceFile() != null) {
                removeSavepointFiles(formController.getInstanceFile().getName());
            }

            saveToDiskResult.setSaveResult(saveAndExit ? SAVED_AND_EXIT : SAVED, shouldFinalize);
        } catch (EncryptionException e) {
            saveToDiskResult.setSaveErrorMessage(e.getMessage());
            saveToDiskResult.setSaveResult(ENCRYPTION_ERROR, shouldFinalize);
        } catch (Exception e) {
            Timber.e(e);

            saveToDiskResult.setSaveErrorMessage(e.getMessage());
            saveToDiskResult.setSaveResult(SAVE_ERROR, shouldFinalize);
        }

        return saveToDiskResult;
    }

    /**
     * Updates the status and editability for the database row corresponding to the instance that is
     * currently managed by the {@link FormController}. There are three cases:
     * - the instance was opened for edit so its database row already exists
     * - a new instance was just created so its database row doesn't exist and needs to be created
     * - a new instance was created at the start of this editing session but the user has already
     * saved it so its database row already exists
     * <p>
     * Post-condition: the uri field is set to the URI of the instance database row that matches
     * the instance currently managed by the {@link FormController}.
     */
    private void updateInstanceDatabase(boolean incomplete, boolean canEditAfterCompleted) {
        FormController formController = Collect.getInstance().getFormController();
        FormInstance formInstance = formController.getFormDef().getInstance();

        String instancePath = formController.getInstanceFile().getAbsolutePath();
        InstancesRepository instances = new DatabaseInstancesRepository();
        Instance instance = instances.getOneByPath(instancePath);

        Instance.Builder instanceBuilder;
        if (instance != null) {
            instanceBuilder = new Instance.Builder(instance);
        } else {
            instanceBuilder = new Instance.Builder();
        }

        if (instanceName != null) {
            instanceBuilder.displayName(instanceName);
        }

        if (incomplete || !shouldFinalize) {
            instanceBuilder.status(Instance.STATUS_INCOMPLETE);
        } else {
            instanceBuilder.status(Instance.STATUS_COMPLETE);
        }

        instanceBuilder.canEditWhenComplete(canEditAfterCompleted);

        if (instance != null) {
            uri = Uri.withAppendedPath(InstanceProviderAPI.InstanceColumns.CONTENT_URI, instance.getId().toString());
            String geometryXpath = getGeometryXpathForInstance(uri);
            Pair<String, String> geometryContentValues = extractGeometryContentValues(formInstance, geometryXpath);
            if (geometryContentValues != null) {
                instanceBuilder.geometryType(geometryContentValues.first);
                instanceBuilder.geometry(geometryContentValues.second);
            }

            Instance newInstance = new DatabaseInstancesRepository().save(instanceBuilder.build());
            uri = Uri.withAppendedPath(InstanceProviderAPI.InstanceColumns.CONTENT_URI, newInstance.getId().toString());
        } else {
            Timber.i("No instance found, creating");
            try (Cursor c = Collect.getInstance().getContentResolver().query(uri, null, null, null, null)) {
                // retrieve the form definition
                if (c.getCount() != 1) {
                    Timber.w("No matching form found for %s", uri);
                    Timber.w("Instance null: %s", instance == null);
                }
                c.moveToFirst();
                String formname = c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.DISPLAY_NAME));
                String submissionUri = null;
                if (!c.isNull(c.getColumnIndex(FormsProviderAPI.FormsColumns.SUBMISSION_URI))) {
                    submissionUri = c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.SUBMISSION_URI));
                }

                // add missing fields into values
                instanceBuilder.instanceFilePath(new StoragePathProvider().getRelativeInstancePath(instancePath));
                instanceBuilder.submissionUri(submissionUri);
                if (instanceName != null) {
                    instanceBuilder.displayName(instanceName);
                } else {
                    instanceBuilder.displayName(formname);
                }
                String jrformid = c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.JR_FORM_ID));
                String jrversion = c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.JR_VERSION));
                instanceBuilder.jrFormId(jrformid);
                instanceBuilder.jrVersion(jrversion);

                String geometryXpath = c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.GEOMETRY_XPATH));
                Pair<String, String> geometryContentValues = extractGeometryContentValues(formInstance, geometryXpath);
                if (geometryContentValues != null) {
                    instanceBuilder.geometryType(geometryContentValues.first);
                    instanceBuilder.geometry(geometryContentValues.second);
                }
            }

            Instance newInstance = new DatabaseInstancesRepository().save(instanceBuilder.build());
            uri = Uri.withAppendedPath(InstanceProviderAPI.InstanceColumns.CONTENT_URI, newInstance.getId().toString());
        }
    }

    /**
     * Extracts geometry information from the given xpath path in the given instance.
     * <p>
     * Returns a ContentValues object with values set for InstanceColumns.GEOMETRY and
     * InstanceColumns.GEOMETRY_TYPE. Those value are null if anything goes wrong with
     * parsing the geometry and converting it to GeoJSON.
     * <p>
     * Returns null if the given XPath path is null.
     */
    private Pair<String, String> extractGeometryContentValues(FormInstance instance, String xpath) {
        if (xpath == null) {
            return null;
        }

        try {
            XPathExpression expr = XPathParseTool.parseXPath(xpath);
            EvaluationContext context = new EvaluationContext(instance);
            Object result = expr.eval(instance, context);
            if (result instanceof XPathNodeset) {
                XPathNodeset nodes = (XPathNodeset) result;
                if (nodes.size() == 0) {
                    Timber.i("TreeElement is missing for xpath %s!, probably it's just not relevant", xpath);
                    return null;
                }

                // For now, only use the first node found.
                TreeElement element = instance.resolveReference(nodes.getRefAt(0));
                IAnswerData value = element.getValue();

                if (value instanceof GeoPointData) {
                    try {
                        JSONObject json = toGeoJson((GeoPointData) value);
                        Timber.i("Geometry for \"%s\" instance found at %s: %s",
                                instance.getName(), xpath, json);

                        return new Pair<>(json.getString("type"), json.toString());
                    } catch (JSONException e) {
                        Timber.w("Could not convert GeoPointData %s to GeoJSON", value);
                    }
                }
            }
        } catch (XPathException | XPathSyntaxException e) {
            Timber.w(e, "Could not evaluate geometry XPath %s in instance", xpath);
        }

        return new Pair<>(null, null);
    }

    @NonNull
    private JSONObject toGeoJson(GeoPointData data) throws JSONException {
        // For a GeoPointData object, the four fields exposed by getPart() are
        // latitude, longitude, altitude, and accuracy radius, in that order.
        double lat = data.getPart(0);
        double lon = data.getPart(1);

        // In GeoJSON, longitude comes before latitude.
        JSONArray coordinates = new JSONArray();
        coordinates.put(lon);
        coordinates.put(lat);

        JSONObject geometry = new JSONObject();
        geometry.put("type", "Point");
        geometry.put("coordinates", coordinates);
        return geometry;
    }

    /**
     * Return the savepoint file for a given instance.
     */
    static File getSavepointFile(String instanceName) {
        File tempDir = new File(new StoragePathProvider().getOdkDirPath(StorageSubdirectory.CACHE));
        return new File(tempDir, instanceName + ".save");
    }

    /**
     * Return the formIndex file for a given instance.
     */
    public static File getFormIndexFile(String instanceName) {
        File tempDir = new File(new StoragePathProvider().getOdkDirPath(StorageSubdirectory.CACHE));
        return new File(tempDir, instanceName + ".index");
    }

    public static void removeSavepointFiles(String instanceName) {
        File savepointFile = getSavepointFile(instanceName);
        File formIndexFile = getFormIndexFile(instanceName);
        FileUtils.deleteAndReport(savepointFile);
        FileUtils.deleteAndReport(formIndexFile);
    }

    /**
     * Write's the data to the sdcard, and updates the instances content provider.
     * In theory we don't have to write to disk, and this is where you'd add
     * other methods.
     */
    private void exportData(boolean markCompleted, FormSaver.ProgressListener progressListener) throws IOException, EncryptionException {
        FormController formController = Collect.getInstance().getFormController();

        progressListener.onProgressUpdate(TranslationHandler.getString(Collect.getInstance(), R.string.survey_saving_collecting_message));

        ByteArrayPayload payload = formController.getFilledInFormXml();
        // write out xml
        String instancePath = formController.getInstanceFile().getAbsolutePath();

        for (String fileName : tempFiles) {
            mediaUtils.deleteMediaFile(fileName);
        }

        progressListener.onProgressUpdate(TranslationHandler.getString(Collect.getInstance(), R.string.survey_saving_saving_message));

        writeFile(payload, instancePath);

        // Write last-saved instance
        String lastSavedPath = formController.getLastSavedPath();
        writeFile(payload, lastSavedPath);

        // update the uri. We have exported the reloadable instance, so update status...
        // Since we saved a reloadable instance, it is flagged as re-openable so that if any error
        // occurs during the packaging of the data for the server fails (e.g., encryption),
        // we can still reopen the filled-out form and re-save it at a later time.
        updateInstanceDatabase(true, true);

        if (markCompleted) {
            // now see if the packaging of the data for the server would make it
            // non-reopenable (e.g., encryption or other fraction of the form).
            boolean canEditAfterCompleted = formController.isSubmissionEntireForm();
            boolean isEncrypted = false;

            // build a submission.xml to hold the data being submitted
            // and (if appropriate) encrypt the files on the side

            // pay attention to the ref attribute of the submission profile...
            File instanceXml = formController.getInstanceFile();
            File submissionXml = new File(instanceXml.getParentFile(), "submission.xml");

            payload = formController.getSubmissionXml();

            // write out submission.xml -- the data to actually submit to aggregate

            progressListener.onProgressUpdate(
                    TranslationHandler.getString(Collect.getInstance(), R.string.survey_saving_finalizing_message));

            writeFile(payload, submissionXml.getAbsolutePath());

            // see if the form is encrypted and we can encrypt it...
            EncryptionUtils.EncryptedFormInformation formInfo = EncryptionUtils.getEncryptedFormInformation(uri, formController.getSubmissionMetadata());
            if (formInfo != null) {
                // if we are encrypting, the form cannot be reopened afterward
                canEditAfterCompleted = false;
                // and encrypt the submission (this is a one-way operation)...

                progressListener.onProgressUpdate(
                        TranslationHandler.getString(Collect.getInstance(), R.string.survey_saving_encrypting_message));

                EncryptionUtils.generateEncryptedSubmission(instanceXml, submissionXml, formInfo);
                isEncrypted = true;

                analytics.logEvent(AnalyticsEvents.ENCRYPT_SUBMISSION, Collect.getCurrentFormIdentifierHash(), "");
            }

            // At this point, we have:
            // 1. the saved original instanceXml,
            // 2. all the plaintext attachments
            // 2. the submission.xml that is the completed xml (whether encrypting or not)
            // 3. all the encrypted attachments if encrypting (isEncrypted = true).
            //
            // NEXT:
            // 1. Update the instance database (with status complete).
            // 2. Overwrite the instanceXml with the submission.xml
            //    and remove the plaintext attachments if encrypting

            updateInstanceDatabase(false, canEditAfterCompleted);

            if (!canEditAfterCompleted) {
                manageFilesAfterSavingEncryptedForm(instanceXml, submissionXml);
            } else {
                // try to delete the submissionXml file, since it is
                // identical to the existing instanceXml file
                // (we don't need to delete and rename anything).
                if (!submissionXml.delete()) {
                    String msg = "Error deleting " + submissionXml.getAbsolutePath()
                            + " (instance is re-openable)";
                    Timber.w(msg);
                }
            }

            // if encrypted, delete all plaintext files
            // (anything not named instanceXml or anything not ending in .enc)
            if (isEncrypted) {
                // Clear the geometry. Done outside of updateInstanceDatabase to avoid multiple
                // branches and because it has no knowledge of encryption status.
                ContentValues values = new ContentValues();
                values.put(InstanceProviderAPI.InstanceColumns.GEOMETRY, (String) null);
                values.put(InstanceProviderAPI.InstanceColumns.GEOMETRY_TYPE, (String) null);
                try {
                    int updated = Collect.getInstance().getContentResolver().update(uri, values, null, null);
                    if (updated < 1) {
                        Timber.w("Instance geometry not cleared after encryption");
                    }
                } catch (IllegalArgumentException e) {
                    Timber.w(e);
                }

                if (!EncryptionUtils.deletePlaintextFiles(instanceXml, new File(lastSavedPath))) {
                    Timber.e("Error deleting plaintext files for %s", instanceXml.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Returns the XPath path of the geo feature used for mapping that corresponds to the blank form
     * that the instance with the given uri is an instance of.
     */
    private static String getGeometryXpathForInstance(Uri uri) {
        try (Cursor instanceCursor = Collect.getInstance().getContentResolver().query(uri, new String[]{InstanceProviderAPI.InstanceColumns.JR_FORM_ID, InstanceProviderAPI.InstanceColumns.JR_VERSION}, null, null, null)) {
            if (instanceCursor.moveToFirst()) {
                String jrFormId = instanceCursor.getString(0);
                String version = instanceCursor.getString(1);

                Form form = new DatabaseFormsRepository().getLatestByFormIdAndVersion(jrFormId, version);
                if (form != null) {
                    return form.getGeometryXpath();
                }
            }
        }
        return null;
    }

    static void manageFilesAfterSavingEncryptedForm(File instanceXml, File submissionXml) throws IOException {
        // AT THIS POINT, there is no going back.  We are committed
        // to returning "success" (true) whether or not we can
        // rename "submission.xml" to instanceXml and whether or
        // not we can delete the plaintext media files.
        //
        // Handle the fall-out for a failed "submission.xml" rename
        // in the InstanceUploaderTask task.  Leftover plaintext media
        // files are handled during form deletion.

        // delete the restore Xml file.
        if (!instanceXml.delete()) {
            String msg = "Error deleting " + instanceXml.getAbsolutePath()
                    + " prior to renaming submission.xml";
            Timber.e(msg);
            throw new IOException(msg);
        }

        // rename the submission.xml to be the instanceXml
        if (!submissionXml.renameTo(instanceXml)) {
            String msg =
                    "Error renaming submission.xml to " + instanceXml.getAbsolutePath();
            Timber.e(msg);
            throw new IOException(msg);
        }
    }

    /**
     * Writes payload contents to the disk.
     */
    static void writeFile(ByteArrayPayload payload, String path) throws IOException {
        File file = new File(path);
        if (file.exists() && !file.delete()) {
            throw new IOException("Cannot overwrite " + path + ". Perhaps the file is locked?");
        }

        // create data stream
        InputStream is = payload.getPayloadStream();
        int len = (int) payload.getLength();

        // read from data stream
        byte[] data = new byte[len];
        int read = is.read(data, 0, len);
        if (read > 0) {
            // Make sure the directory path to this file exists.
            file.getParentFile().mkdirs();
            // write xml file
            RandomAccessFile randomAccessFile = null;
            try {
                randomAccessFile = new RandomAccessFile(file, "rws");
                randomAccessFile.write(data);
            } finally {
                if (randomAccessFile != null) {
                    try {
                        randomAccessFile.close();
                    } catch (IOException e) {
                        Timber.e(e, "Error closing RandomAccessFile: %s", path);
                    }
                }
            }
        }
    }
}
