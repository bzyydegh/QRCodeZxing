package cn.edu.gdaibzyy.qrcodezxing.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import cn.edu.gdaibzyy.qrcodezxing.R;
import cn.edu.gdaibzyy.qrcodezxing.camera.CameraManager;
import cn.edu.gdaibzyy.qrcodezxing.decode.CaptureActivityHandler;
import cn.edu.gdaibzyy.qrcodezxing.decode.InactivityTimer;
import cn.edu.gdaibzyy.qrcodezxing.decode.RGBLuminanceSource;
import cn.edu.gdaibzyy.qrcodezxing.utils.DensityUtil;
import cn.edu.gdaibzyy.qrcodezxing.view.ViewfinderView;

/**
 * 初始化相机
 * @author Administrator
 */
@SuppressWarnings("deprecation")
public class CaptureActivity extends Activity implements Callback {

	private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
	private boolean hasSurface;
	private Vector<BarcodeFormat> decodeFormats;
	private String characterSet;
	private InactivityTimer inactivityTimer;
	private MediaPlayer mediaPlayer;
	private boolean playBeep;
	private static final float BEEP_VOLUME = 0.10f;
	private boolean vibrate;
	private ImageView cancelScanImageView;
    private ImageView mScannerLight;

	int ifOpenLight = 0; // 判断是否开启闪光灯

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_qrcode_camera);
		// ViewUtil.addTopView(getApplicationContext(), this,
		// R.string.scan_card);
		CameraManager.init(getApplication());
		viewfinderView = (ViewfinderView) findViewById(R.id.qrcode_viewfinder_view);
		cancelScanImageView = (ImageView) this.findViewById(R.id.qrcode_scanner_back);
        mScannerLight=(ImageView) this.findViewById(R.id.mo_scanner_light);
		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.qrcode_preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		decodeFormats = null;
		characterSet = null;

		playBeep = true;
		AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
			playBeep = false;
		}
		initBeepSound();
		vibrate = true;

		//停止扫描
		cancelScanImageView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				CaptureActivity.this.finish();
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		CameraManager.get().closeDriver();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	/**
	 * Handler scan result
	 * @param result
	 * @param barcode 获取结果
	 */
	public void handleDecode(Result result, Bitmap barcode) {
		inactivityTimer.onActivity();
		playBeepSoundAndVibrate();
		String resultString = result.getText();
		// FIXME
		if (resultString.equals("")) {
			Toast.makeText(CaptureActivity.this, "扫描失败!", Toast.LENGTH_SHORT)
					.show();
		} else {
			Log.i("二维码扫描结果",resultString);
			Intent resultIntent = new Intent();
			resultIntent.putExtra("qrcode_result",resultString);
			CaptureActivity.this.setResult(1, resultIntent);
		}
		CaptureActivity.this.finish();
	}

	/**
	 * 获取带二维码的相片进行扫描
	 */
	public void pickPictureFromAblum(View v) {
		Intent i = new Intent(
				Intent.ACTION_PICK,
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

		startActivityForResult(i, 1);
	}

	/**
	 * (non-Javadoc)
	 * @see Activity#onActivityResult(int, int,
	 * Intent) 对相册获取的结果进行分析
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case 1:
					Uri selectedImage = data.getData();
					String[] filePathColumn = { MediaStore.Images.Media.DATA };

					Cursor cursor1 = getContentResolver().query(selectedImage,
							filePathColumn, null, null, null);
					cursor1.moveToFirst();

					int columnIndex = cursor1.getColumnIndex(filePathColumn[0]);
					String picturePath = cursor1.getString(columnIndex);
					cursor1.close();

					Result resultString = scanningImage1(picturePath);
					if (resultString == null){
						Toast.makeText(getApplicationContext(), "对不起，未识别二维码，您确认你选择的图片中包含二维码！", Toast.LENGTH_LONG).show();
					}else{
						String resultImage = resultString.getText();
						if (resultImage.equals("")) {
							Toast.makeText(CaptureActivity.this, "扫描失败!",
									Toast.LENGTH_SHORT).show();
						} else {
							Log.i("二维码扫描结果",resultImage);
							Intent resultIntent = new Intent();
							resultIntent.putExtra("qrcode_result",resultImage);
							CaptureActivity.this.setResult(1, resultIntent);
						}
						CaptureActivity.this.finish();
					}
					break;
				default:
					break;
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	// 解析QR图片
	private Result scanningImage1(String picturePath) {

		if (TextUtils.isEmpty(picturePath)) {
			return null;
		}
		
		Map<DecodeHintType, String> hints1 = new Hashtable<DecodeHintType, String>();
		hints1.put(DecodeHintType.CHARACTER_SET, "utf-8");

		// 获得待解析的图片
		Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
		RGBLuminanceSource source = new RGBLuminanceSource(bitmap);
		BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
		QRCodeReader reader = new QRCodeReader();
		Result result;
		try {
			result =  reader.decode(bitmap1, (Hashtable<DecodeHintType, String>) hints1);
			// 得到解析后的文字
			Toast.makeText(CaptureActivity.this, result.getText(),
					Toast.LENGTH_LONG).show();

			return result;
			
		} catch (Exception e) {
			Toast.makeText(CaptureActivity.this, "对不起，未识别二维码，您确认你选择的图片中包含二维码！",
					Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
		return null;
	}

	// 是否开启闪光灯
	public void IfOpenLight(View v) {
		ifOpenLight++;

		switch (ifOpenLight % 2) {
		case 0:
			// 关闭
			CameraManager.get().closeLight();
            mScannerLight.setBackgroundResource(R.drawable.qrcode_circle_trans_black);
			break;

		case 1:
			// 打开
			CameraManager.get().openLight(); // 开闪光灯
            mScannerLight.setBackgroundResource(R.drawable.qrcode_circle_trans_red);
			break;
		default:
			break;
		}
        mScannerLight.setPadding(DensityUtil.dip2px(this,8),DensityUtil.dip2px(this,8),
                DensityUtil.dip2px(this,8),DensityUtil.dip2px(this,8));
	}


	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);
		} catch (Exception ioe) {
			return;
		}
		if (handler == null) {
			handler = new CaptureActivityHandler(this, decodeFormats,
					characterSet);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	private void initBeepSound() {
		if (playBeep && mediaPlayer == null) {
			// The volume on STREAM_SYSTEM is not adjustable, and users found it
			// too loud,
			// so we now play on the music stream.
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setOnCompletionListener(beepListener);

			AssetFileDescriptor file = getResources().openRawResourceFd(
					R.raw.beep);
			try {
				mediaPlayer.setDataSource(file.getFileDescriptor(),
						file.getStartOffset(), file.getLength());
				file.close();
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
				mediaPlayer.prepare();
			} catch (IOException e) {
				mediaPlayer = null;
			}
		}
	}

	private static final long VIBRATE_DURATION = 200L;

	private void playBeepSoundAndVibrate() {
		if (playBeep && mediaPlayer != null) {
			mediaPlayer.start();
		}
		if (vibrate) {
			Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

	/**
	 * When the beep has finished playing, rewind to queue up another one.
	 */
	private final OnCompletionListener beepListener = new OnCompletionListener() {
		public void onCompletion(MediaPlayer mediaPlayer) {
			mediaPlayer.seekTo(0);
		}
	};

}