package edu.mines.toilers.places;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

//import javax.crypto.Cipher;
//import javax.crypto.CipherOutputStream;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import edu.mines.toilers.places.R;

public class MainActivity extends Activity {

	public static final String PREFS_NAME = "LogFile";
	public static final String START_PREFS = "StartFile";
	public static final String OUTPUT_NAME = "Important_Places_Log.txt";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences start_prefs = getSharedPreferences(START_PREFS, 0);
		if (start_prefs.getInt("START", -99) == -99) { // first run of app
			SharedPreferences.Editor editor = start_prefs.edit();
			editor.putInt("START",
					Calendar.getInstance().get(Calendar.DAY_OF_YEAR));
			editor.commit();
		}
		setContentView(R.layout.activity_main);
		TextView timeField = (TextView) findViewById(R.id.textView5);
		timeField.setText(Math.max(start_prefs.getInt("START", -99) + 30
				- Calendar.getInstance().get(Calendar.DAY_OF_YEAR), 0)
				+ ""); // days remaining
		TextView idField = (TextView) findViewById(R.id.textView1);
		String id = getID();
		if (id != null && !id.equals("")) { // can get id
			if (!prefs.getString(id, "*").equals("*")) { // previously logged
				Toast.makeText(getApplicationContext(),
						"Location logged previously", Toast.LENGTH_SHORT)
						.show();
				if (getIntent().getExtras() != null
						&& getIntent().getExtras().getString("ORIGIN")
								.equals("CALL")) { // call initiated activity
					addToLog(id, System.currentTimeMillis() + "");
					finish();
				}
			}
			idField.setText(id);
		} else
			Toast.makeText(getApplicationContext(), "Cannot retreive CellID",
					Toast.LENGTH_SHORT).show();
	}

	public void onLog(View view) {
		RadioGroup allOptions = (RadioGroup) findViewById(R.id.radioGroup1);
		RadioButton selected = (RadioButton) allOptions.findViewById(allOptions
				.getCheckedRadioButtonId());
		String type = (String) selected.getText();
		String id = getID();
		if (id != null && !id.equals("")) { // can get id
			SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
			if (prefs.getString(id, "*").equals("*")) { // not yet logged
				addToLog(id, type + "," + System.currentTimeMillis());
				Toast.makeText(getApplicationContext(),
						"Location logged as " + type, Toast.LENGTH_SHORT)
						.show();
				if (getIntent().getExtras() != null
						&& getIntent().getExtras().getString("ORIGIN")
								.equals("CALL"))
					finish();
			} else {
				addToLog(id, System.currentTimeMillis() + "");
				Toast.makeText(getApplicationContext(),
						"Location logged again", Toast.LENGTH_SHORT).show();
			}
		} else {
			Toast.makeText(getApplicationContext(), "Cannot retreive CellID",
					Toast.LENGTH_SHORT).show();
			return;
		}

	}

	public void onExport(View view) {

		SharedPreferences start_prefs = getSharedPreferences(START_PREFS, 0);
		int daysRemaining = Math.max(start_prefs.getInt("START", -99) + 30
				- Calendar.getInstance().get(Calendar.DAY_OF_YEAR), 0);
		if (daysRemaining > 0) {
			Toast.makeText(getApplicationContext(),
					"Can't export until research period has completed",
					Toast.LENGTH_SHORT).show(); 
			// return;
		}

		if (!isExternalStorageWritable()) {
			Toast.makeText(getApplicationContext(),
					"Can't write to external storage, canceling",
					Toast.LENGTH_SHORT).show();
			return;
		}
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
		Map<String, ?> data = prefs.getAll();
		File root = android.os.Environment.getExternalStorageDirectory();
		root.mkdirs();
		File file = new File(root, OUTPUT_NAME);
		try {
			KeyGenerator kgen = KeyGenerator.getInstance("AES");
			kgen.init(128);
			SecretKey key = kgen.generateKey();
			byte[] aesKey = key.getEncoded();
			SecretKeySpec aeskeySpec = new SecretKeySpec(aesKey, "AES");
			Cipher myCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			myCipher.init(Cipher.ENCRYPT_MODE, aeskeySpec);

			FileOutputStream f = new FileOutputStream(file);
			Thread.sleep(100);
			CipherOutputStream cos = new CipherOutputStream(f, myCipher);
			PrintWriter pw = new PrintWriter(cos);
			for (String s : data.keySet()) {
				pw.println(s
						+ ","
						+ ((String) data.get(s)).replace("Home", "0")
								.replace("Work", "1").replace("Other", "2"));
			}
			pw.flush();
			pw.close();
			f.close();
			Toast.makeText(getApplicationContext(),
					"Data exported to " + OUTPUT_NAME + " on external storage",
					Toast.LENGTH_SHORT).show();
			// Send Email
			Intent sharingIntent = new Intent(Intent.ACTION_SEND);
			sharingIntent.setType("vnd.android.cursor.dir/email");
			String to[] = { "daniel@mawhirter.com" };
			sharingIntent.putExtra(Intent.EXTRA_EMAIL, to);
			sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
			sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "subject");
			sharingIntent.putExtra(Intent.EXTRA_TEXT, new String(aesKey));
			startActivity(Intent.createChooser(sharingIntent, "Send email"));
			finish();
		} catch (Exception e) {
			logException(e);
		}
	}

	private String getID() {
		String cellId = "";
		TelephonyManager phoneManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		switch (phoneManager.getPhoneType()) {
		case TelephonyManager.PHONE_TYPE_GSM:
			GsmCellLocation gsmCellLocation = (GsmCellLocation) phoneManager
					.getCellLocation();
			if (gsmCellLocation != null)
				cellId = phoneManager.getNetworkOperator() + ":"
						+ gsmCellLocation.getLac() + ":"
						+ gsmCellLocation.getCid();
			break;
		case TelephonyManager.PHONE_TYPE_CDMA:
			CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) phoneManager
					.getCellLocation();
			if (cdmaCellLocation != null)
				cellId = phoneManager.getNetworkOperator() + ":"
						+ cdmaCellLocation.getSystemId() + ":"
						+ cdmaCellLocation.getNetworkId() + ":"
						+ cdmaCellLocation.getBaseStationId();
			break;
		}
		return cellId;
	}

	private boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}

	private void addToLog(String id, String toAdd) {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
		String current = prefs.getString(id, "");
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(id, current + toAdd + ",");
		editor.commit();
	}

	private void logException(Exception e) {
		ByteArrayOutputStream logger = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(logger);
		e.printStackTrace(ps);
		Log.e("edu.mines.toilers.places", logger.toString());
	}
}