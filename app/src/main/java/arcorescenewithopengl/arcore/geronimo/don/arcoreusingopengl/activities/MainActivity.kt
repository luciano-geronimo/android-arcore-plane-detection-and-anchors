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
import java.util.ArrayList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    // Anchors created from taps used for object placing with a given color.
    private class ColoredAnchor(val anchor: Anchor, val color: Array<Float>)

    val APP_TAG = "MeuAR"
    private val messageSnackbarHelper = SnackbarHelper()
    private val backgroundRenderer = BackgroundRenderer()
    private val virtualObject = ObjectRenderer()
    private val planeRenderer = PlaneRenderer()
    private val pointCloudRenderer = PointCloudRenderer()
    private val anchors = ArrayList<ColoredAnchor>()

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
            //O meu cubinho idiota
            virtualObject.createOnGlThread(/*context=*/this, "models/untitled.obj", "models/face.png")
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f)
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
            handleTap(frame!!, camera!!)
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

            val anchorMatrix = FloatArray(16)
            //desenho das anchors
            val scaleFactor = 0.1f
            for(coloredAnchor in anchors){
                if ( coloredAnchor.anchor.trackingState != TrackingState.TRACKING){
                    continue // ignora as que não estejam sendo rastradas
                }
                coloredAnchor.anchor.pose.toMatrix(anchorMatrix, 0)
                virtualObject.updateModelMatrix(anchorMatrix, scaleFactor)
                var color : FloatArray = FloatArray(coloredAnchor.color.size);
                color[0] = coloredAnchor.color[0]
                color[1] = coloredAnchor.color[1]
                color[2] = coloredAnchor.color[2]
                color[3] = coloredAnchor.color[3]
                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, color)
            }
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
    //Serve pra tratar os touches
    private fun handleTap(frame:Frame, camera:Camera){
        val tap = tapHelper.poll()
        if(tap != null && camera.trackingState == TrackingState.TRACKING){
            val hits = frame.hitTest(tap)
            for (hit in hits){
                val trackable = hit.trackable
                if (//Cria uma anchor se um plano ou ponto orientado foi atingido
                    (trackable is Plane
                    && trackable.isPoseInPolygon(hit.hitPose)
                    && (PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose)>0))
                    ||
                    (trackable is Point && trackable.orientationMode ==Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                    )
                 {
                     //Limite pras anchors. Anchors são caras. Remove a 1a
                     if (anchors.size >= 20){
                         anchors[0].anchor.detach()
                         anchors.removeAt(0)
                     }
                     var objColor= arrayOf(0.0f, 0.0f, 0.0f, 1.0f)
                     if(trackable is Point){
                         objColor = arrayOf(66.0f, 133.0f, 244.0f, 255.0f);
                     }
                     else if(trackable is Plane){
                         objColor = arrayOf(139.0f, 195.0f, 74.0f, 255.0f)
                     }
                     anchors.add(ColoredAnchor(hit.createAnchor(), objColor))
                     break
                 }//Fim do loop que trata o touch ter interceptado um plano ou ponto.
            }
        }
    }
}
