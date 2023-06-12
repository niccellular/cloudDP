
package com.atakmap.android.clouddatapackage;

import static com.tozny.crypto.android.AesCbcWithIntegrity.generateKeyFromPassword;
import static com.tozny.crypto.android.AesCbcWithIntegrity.generateSalt;
import static com.tozny.crypto.android.AesCbcWithIntegrity.saltString;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.clouddatapackage.plugin.CloudDatapackageLifecycle;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.clouddatapackage.plugin.R;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.coremap.log.Log;
import com.scottyab.aescrypt.AESCrypt;
import com.tozny.crypto.android.AesCbcWithIntegrity;

import org.w3c.dom.Text;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;


public class CloudDatapackageDropDownReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String TAG = "atak-code";

    public static final String SHOW_PLUGIN = "com.atakmap.android.clouddatapackage.SHOW_PLUGIN";
    private final View templateView;
    private final Context pluginContext;
    private final MapView _mapView;

    private Button upload, download, clear;

    private TextView codes, filenames;

    /**************************** CONSTRUCTOR *****************************/

    public CloudDatapackageDropDownReceiver(final MapView mapView,
            final Context context) {
        super(mapView);
        this.pluginContext = context;
        this._mapView = mapView;
        // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
        // In this case, using it is not necessary - but I am putting it here to remind
        // developers to look at this Inflator
        templateView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);
    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_PLUGIN)) {

            Log.d(TAG, "showing plugin drop down");
            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);

            filenames = templateView.findViewById(R.id.filenames);
            codes = templateView.findViewById(R.id.codes);
            codes.setTextIsSelectable(true);
            updateCodes();

            clear = templateView.findViewById(R.id.clear);
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences sharedPref = CloudDatapackageLifecycle.activity.getSharedPreferences("atak-code", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.clear();
                    editor.apply();
                    updateCodes();
                }
            });

            upload = templateView.findViewById(R.id.upload);
            upload.setOnClickListener(view -> ImportFileBrowserDialog.show(
                    "Upload to atak.zip",
                    Environment.getExternalStorageDirectory() + File.separator,
                    new String[]{"*"},
                    new ImportFileBrowserDialog.DialogDismissed() {
                        @Override
                        public void onFileSelected(final File file) {
                            if (FileSystemUtils.isFile(file)) {
                                /*
                                SharedPreferences sharedPref = CloudDatapackageLifecycle.activity.getSharedPreferences("atak-code", Context.MODE_PRIVATE);
                                String already = sharedPref.getString(file.getName(), "");
                                Log.d(TAG, already);
                                if (!already.isEmpty()) {
                                    Toast.makeText(MapView._mapView.getContext(), "Hash already sent!", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                */
                                if (file.length()/1024 > 5000) {
                                    Toast.makeText(getMapView().getContext(), "File too big, current limit is 5MB", Toast.LENGTH_LONG).show();
                                    Log.d(TAG, "File too big");
                                    return;
                                }
                                final String[] userPassword = new String[1];
                                final File[] encryptedFile = new File[1];

                                AlertDialog.Builder builderSingle = new AlertDialog.Builder(_mapView.getContext());
                                builderSingle.setTitle("Encryption Key");
                                builderSingle.setMessage("Enter a password to encrypt with");
                                final EditText password = new EditText(_mapView.getContext());
                                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                                builderSingle.setView(password);

                                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(pluginContext, android.R.layout.select_dialog_singlechoice);
                                builderSingle.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        userPassword[0] = password.getText().toString();
                                        if (userPassword[0].isEmpty()) {
                                            Toast.makeText(getMapView().getContext(), "No password", Toast.LENGTH_LONG).show();
                                            Log.d(TAG, "Upload: no password");
                                        } else {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        encryptedFile[0] = encrypt(password.getText().toString(), file);
                                                        CloudDatapackageLifecycle.activity.runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                Toast.makeText(getMapView().getContext(), "Uploading file: " + file.getName(), Toast.LENGTH_LONG).show();
                                                            }
                                                        });

                                                        Log.i(TAG, String.format("Uploading file: %s", file.getAbsolutePath()));
                                                        new Thread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                try {
                                                                    upload(encryptedFile[0]);
                                                                    updateCodes();
                                                                } catch (IOException |
                                                                         NoSuchAlgorithmException e) {
                                                                    e.printStackTrace();
                                                                }
                                                            }
                                                        }).start();
                                                    } catch (IOException |
                                                             GeneralSecurityException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                }
                                            }).start();
                                        }
                                    }
                                });

                                builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                                builderSingle.setAdapter(arrayAdapter, null);
                                builderSingle.show();
                            }
                        }
                        @Override
                        public void onDialogClosed() {
                            Toast.makeText(getMapView().getContext(), "No file selected!", Toast.LENGTH_LONG).show();
                        }
                    }, getMapView().getContext()
            ));

            download = templateView.findViewById(R.id.download);
            download.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String[] userPassword = new String[1];

                    AlertDialog.Builder builderSingle = new AlertDialog.Builder(_mapView.getContext());
                    builderSingle.setTitle("Download from atak.zip");
                    builderSingle.setMessage("Enter your download code and password");
                    final EditText inputcode = new EditText(_mapView.getContext());
                    inputcode.setHint("Download code");
                    inputcode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    inputcode.setMaxWidth(512);

                    final EditText password = new EditText(_mapView.getContext());
                    password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    password.setHint("Password");
                    password.setMaxWidth(512);

                    final LinearLayout ll = new LinearLayout(_mapView.getContext());
                    ll.addView(inputcode);
                    ll.addView(password);

                    builderSingle.setView(ll);

                    final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(pluginContext, android.R.layout.select_dialog_singlechoice);
                    builderSingle.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            userPassword[0] = password.getText().toString();
                            if (userPassword[0].isEmpty() || inputcode.getText().toString().isEmpty()) {
                                Toast.makeText(getMapView().getContext(), "Invalid Download Code or Password", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Invalid Download Code or Password");
                            } else if (inputcode.getText().toString().length() < 8) {
                                Toast.makeText(getMapView().getContext(), "Invalid Download Code", Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Invalid Download Code");
                            } else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (download(inputcode.getText().toString(), userPassword[0])) {
                                                updateCodes();
                                            } else {
                                                CloudDatapackageLifecycle.activity.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(getMapView().getContext(), "Download failed, bad Download Code or Password.", Toast.LENGTH_LONG).show();
                                                    }
                                                });

                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }).start();
                            }
                        }
                    });

                    builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builderSingle.setAdapter(arrayAdapter, null);
                    builderSingle.show();

                }
            });
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

    private void decrypt(String password, File file) throws GeneralSecurityException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        String hash = bytesToHex(digest.digest(password.getBytes()));
        if (hash.isEmpty()){
            hash = "ABCDEFGHIJKLMN";
        }

        String salt = saltString(hash.getBytes());
        AesCbcWithIntegrity.SecretKeys key = generateKeyFromPassword(hash, salt);

        byte[] message = Files.readAllBytes(file.toPath());
        byte[] cipherText = Arrays.copyOfRange(message, 48, message.length);
        byte[] mac = Arrays.copyOfRange(message, 16, 48);
        byte[] iv = Arrays.copyOfRange(message, 0, 16);

        AesCbcWithIntegrity.CipherTextIvMac cipherTextIvMac = new AesCbcWithIntegrity.CipherTextIvMac(cipherText, iv, mac);

        File decryptedFile = new File(String.format("/sdcard/atak/tools/datapackage/%s", file.getName()));
        if (decryptedFile.exists()) {
            Log.d(TAG, "Deleting existing encrypted file");
            decryptedFile.delete();
        }
        OpenOption[] options = new OpenOption[]{WRITE, CREATE_NEW};
        Files.write(decryptedFile.toPath(), AesCbcWithIntegrity.decrypt(cipherTextIvMac, key), options);

        CloudDatapackageLifecycle.activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getMapView().getContext(), "Importing datapackage: " + file.getName(), Toast.LENGTH_LONG).show();
            }
        });
    }
    private File encrypt(String password, File file) throws IOException, GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        String hash = bytesToHex(digest.digest(password.getBytes()));
        if (hash.isEmpty()){
            hash = "ABCDEFGHIJKLMN";
        }

        String salt = saltString(hash.getBytes());
        AesCbcWithIntegrity.SecretKeys key = generateKeyFromPassword(hash, salt);

        byte[] message = Files.readAllBytes(file.toPath());
        AesCbcWithIntegrity.CipherTextIvMac cipherTextIvMac = AesCbcWithIntegrity.encrypt(message, key);

        File encryptedFile = new File(String.format("/sdcard/atak/tmp/%s", file.getName()));
        if (encryptedFile.exists()) {
            Log.d(TAG, "Deleting existing encrypted file");
            encryptedFile.delete();
        }
        OpenOption[] options = new OpenOption[]{WRITE, CREATE_NEW};
        Files.write(encryptedFile.toPath(), cipherTextIvMac.getIv(), options);
        options = new OpenOption[]{WRITE, APPEND};
        Files.write(encryptedFile.toPath(), cipherTextIvMac.getMac(), options);
        Files.write(encryptedFile.toPath(), cipherTextIvMac.getCipherText(), options);

        return encryptedFile;
    }

    private void updateCodes() {
        CloudDatapackageLifecycle.activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sharedPref = CloudDatapackageLifecycle.activity.getSharedPreferences("atak-code", Context.MODE_PRIVATE);
                Map<String, ?> map = sharedPref.getAll();

                if (map.size() == 0) {
                    filenames.setText("");
                    codes.setText("");
                    return;
                }

                for (Map.Entry<String, ?> entry: map.entrySet()) {
                    Log.d(TAG, entry.getKey() + ":" + entry.getValue().toString());
                    String name = entry.getKey();
                    if (name.length() > 32) {
                        name = name.substring(0,32);
                    }
                    filenames.setText(filenames.getText() + "\n" + name);
                    codes.setText(codes.getText() + "\n" + entry.getValue().toString().substring(0,8));
                }
            }
        });
    }
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private boolean download(String atakcode, String password) throws IOException {
        if (atakcode.isEmpty())
            return false;

        URL url = new URL("https://atak.zip/");
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            urlConnection.setInstanceFollowRedirects(true);
            urlConnection.setDoInput(true);
            urlConnection.setRequestProperty("atak-code", atakcode.substring(0,8));

            String size = "";
            String name = "fail";
            Map<String, List<String>> map = urlConnection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                if (entry.getKey() == null)
                    continue;
                String key = entry.getKey();
                if (key.equalsIgnoreCase("content-length")) {
                    size = entry.getValue().iterator().next();
                } else if (key.equalsIgnoreCase("file-name")) {
                    name = entry.getValue().iterator().next();
                }
            }

            if (size.isEmpty() || name.equalsIgnoreCase("fail")) {
                return false;
            }

            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            byte[] response = new byte[Integer.parseInt(size)];
            in.read(response, 0, response.length);

            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            String hash = bytesToHex(digest.digest(response));

            if (hash.startsWith(atakcode)) {
                Log.d(TAG, "Download hash match, saving to disk");
                Path p = Paths.get(name);
                String filename = p.getFileName().toString();
                Log.d(TAG, String.format("Downloaded filename is: %s", filename));

                File f = new File(String.format("/sdcard/atak/tmp/%s.zip", filename.substring(0, filename.indexOf("."))));
                FileOutputStream fso = new FileOutputStream(f);
                fso.write(response);
                fso.close();
                decrypt(password, f);
                SharedPreferences sharedPref = CloudDatapackageLifecycle.activity.getSharedPreferences("atak-code", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(filename, atakcode);
                editor.apply();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "download failed");
        } finally {
            urlConnection.disconnect();
        }

        return false;
    }

    private void upload(File file) throws IOException, NoSuchAlgorithmException {
        byte[] b = FileSystemUtils.read(file);

        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        String hash = bytesToHex(digest.digest(b));
        Log.d(TAG, String.format("Local SHA512: %s", hash));

        URL url = new URL(String.format("https://atak.zip/%s", file.getName()));
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            urlConnection.setFixedLengthStreamingMode(b.length);
            urlConnection.setInstanceFollowRedirects(true);
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.setRequestMethod("PUT");
            urlConnection.setRequestProperty("atak-code", hash.substring(0,8));
            urlConnection.setRequestProperty("file", file.getName());
            urlConnection.setRequestProperty("Content-length", String.valueOf(b.length));

            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            out.write(b);
            out.close();

            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            byte[] response = new byte[128];
            in.read(response, 0, 128);
            String replyHash = new String(response);

            if (replyHash.equals(hash)) {
                Log.d(TAG, "hash matches, storing to disk");
                SharedPreferences sharedPref = CloudDatapackageLifecycle.activity.getSharedPreferences("atak-code", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(file.getName(), hash);
                editor.apply();
            } else {
                CloudDatapackageLifecycle.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getMapView().getContext(), "Hash mismatch, upload failed!", Toast.LENGTH_LONG).show();
                    }
                });
            }
        } finally {
            urlConnection.disconnect();
        }
    }
}
