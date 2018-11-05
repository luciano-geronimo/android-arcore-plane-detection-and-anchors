package arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.activities

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.R
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.helpers.*
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.rendering.BackgroundRenderer
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.rendering.ObjectRenderer
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.rendering.PlaneRenderer
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.rendering.PointCloudRenderer
import com.google.ar.core.*
import kotlinx.android.synthetic.main.activity_main.*

import com.google.ar.core.exceptions.*
import java.io.IOException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    val APP_TAG = "MeuAR"
    private val messageSnackbarHelper = SnackbarHelper()
    private val backgroundRenderer = BackgroundRenderer()
    private val virtualObject = ObjectRenderer()
    private val virtualObjectShadow = ObjectRenderer()
    private val planeRenderer = PlaneRenderer()
    private val pointCloudRenderer = PointCloudRenderer()

    private val snackbarHelper : SnackbarHelper = SnackbarHelper()
    private lateinit var displayRotationHelper : DisplayRotationHelper
    private lateinit var tapHelper : TapHelper
    private var installRequested : Boolean = false
    private var session : Session? = null//A sesion do arcore.
    /**
     * Caso a surface seja modificada eu tenho que mudar a viewport do opengl.
     * */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }
    /**
     * Se estou criando a surface eu tenho que criar os renderabled
     * */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        try{
            backgroundRenderer.createOnGlThread(this)
            planeRenderer.createOnGlThread(this, "trigrid.png")
            pointCloudRenderer.createOnGlThread(this)
        }
        catch (e:IOException){
            Log.e(APP_TAG, "Error reading asset file",e)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        // Limpa os buffers.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        //Se o session tá null pára tudo. O resto depende do Session
        if (session == null) {
            return
        }
        // Quando o tamanho da viewport muda o ARCORE precisa ajustar a matrix de perspectiva e o background de video
        displayRotationHelper.updateSessionIfNeeded(session)
        try {
            session?.setCameraTextureName(backgroundRenderer.textureId)//O background renderer tem a textura com a imagem da cämera
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            val frame = session?.update()
            val camera = frame?.camera
//Depois vejo isso qdo importar um objeto.
//            // Handle one tap per frame.
//            handleTap(frame, camera)
            //Renderização da câmera (o background)
            backgroundRenderer.draw(frame)
            // If not tracking, don't draw 3d objects.
            if (camera?.trackingState == TrackingState.PAUSED) {
                return
            }
            // Get projection matrix. (P da MVP matrix)
            val projmtx = FloatArray(16)
            camera?.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)
            // Get camera matrix and draw. (V da MVP matrix)
            val viewmtx = FloatArray(16)
            camera?.getViewMatrix(viewmtx, 0)
            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            val colorCorrectionRgba = FloatArray(4)
            frame?.lightEstimate?.getColorCorrection(colorCorrectionRgba, 0)
            // Visualize tracked points.
            val pointCloud = frame?.acquirePointCloud()//a nuvem de pontos que o arcore detectou
            pointCloudRenderer.update(pointCloud)
            pointCloudRenderer.draw(viewmtx, projmtx)
            // Application is responsible for releasing the point cloud resources after
            // using it.
            pointCloud?.release()
            //Se achou pelo menos um plano esconde a snackbar
            if (messageSnackbarHelper.isShowing()) {
                val trackables = session?.getAllTrackables(Plane::class.java)
                for(p in trackables!!){
                    if(p.trackingState == TrackingState.TRACKING){
                        messageSnackbarHelper.hide(this)
                        break
                    }
                }
            }
            //desenho dos planos encontrados é agora
            val planes = session!!.getAllTrackables(Plane::class.java)
            val orientedPose = camera!!.displayOrientedPose
            planeRenderer.drawPlanes(planes, orientedPose, projmtx)
//FAÇO DEPOIS, AINDA N TENHO TOUCH
//            // Visualize anchors created by touch.
//            val scaleFactor = 1.0f
//            for (coloredAnchor in anchors) {
//                if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
//                    continue
//                }
//                // Get the current pose of an Anchor in world space. The Anchor pose is updated
//                // during calls to session.update() as ARCore refines its estimate of the world.
//                coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0)
//
//                // Update and draw the model and its shadow.
//                virtualObject.updateModelMatrix(anchorMatrix, scaleFactor)
//                virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor)
//                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color)
//                virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color)
//            }

        } catch (t: Throwable) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(APP_TAG, "Exception on the OpenGL thread", t)
        }

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

        try{
            session?.resume()
        }
        catch (e:CameraNotAvailableException){
            messageSnackbarHelper.showError(this, "Camera not available. Restart app")
            session = null
            return
        }
        surfaceView.onResume()
        displayRotationHelper.onResume()
        messageSnackbarHelper.showMessage(this, "Searching for surfaces...")
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if( !CameraPermissionHelper.hasCameraPermission(this) ){
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }


}
