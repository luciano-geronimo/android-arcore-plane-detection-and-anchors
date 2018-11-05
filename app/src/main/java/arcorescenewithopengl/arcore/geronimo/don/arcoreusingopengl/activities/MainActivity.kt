package arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.activities

import android.opengl.GLSurfaceView
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.R
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.helpers.CameraPermissionHelper
import kotlinx.android.synthetic.main.activity_main.*

import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.helpers.DisplayRotationHelper
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.helpers.SnackbarHelper
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.helpers.TapHelper
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    val APP_TAG = "MeuAR"
    private val snackbarHelper : SnackbarHelper = SnackbarHelper()
    private lateinit var displayRotationHelper : DisplayRotationHelper
    private lateinit var tapHelper : TapHelper
    private var installRequested : Boolean = false
    private var session : Session? = null//A sesion do arcore.
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onDrawFrame(gl: GL10?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    private fun openGlSetup(){
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8,8,8,8,16,0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        displayRotationHelper = DisplayRotationHelper(this)
        openGlSetup()
        tapHelper = TapHelper(this)
        surfaceView.setOnTouchListener(tapHelper)
    }

    override fun onResume() {
        super.onResume()
        if(session == null){
            var exception:Exception? = null
            var errorMessage:String? = null
            try {
                val installStatus = ArCoreApk.getInstance().requestInstall(this, !installRequested)
                //verifica instalação do arcore
                when(installStatus){
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                    }
                }
                //Tem que ter permissão de câmera
                if(!CameraPermissionHelper.hasCameraPermission(this)){
                    CameraPermissionHelper.requestCameraPermission(this)
                    return
                }
                session = Session(this)//pronto, posso criar a sessão de arcore.
            }
            catch (e : UnavailableArcoreNotInstalledException){
                exception = e
                errorMessage = "Instala o arcore faz favor"
            }
            catch (e: UnavailableUserDeclinedInstallationException){
                exception = e
                errorMessage = "Instala o arcore faz favor"
            }
            catch (e: UnavailableApkTooOldException) {
                exception = e
                errorMessage = "Atualize o Arcore"
            }
            catch (e: UnavailableSdkTooOldException) {
                exception = e
                errorMessage = "Atualize o app"
            }
            catch (e:UnavailableDeviceNotCompatibleException) {
                exception = e
                errorMessage = "Esse aparelho n suporta AR"
            }
            catch (e: java.lang.Exception) {
                exception = e
                errorMessage = "Exceção"
            }
            if(errorMessage != null)
            {
                snackbarHelper.showError(this, errorMessage)
                Log.e(APP_TAG, "Exceção na criação da sessão ${exception?.toString()}")
                return
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(session != null){
//            // Note that the order matters - GLSurfaceView is paused first so that it does not try
//            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
//            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause()
            surfaceView.onPause()
            session?.pause()
        }
    }

}
