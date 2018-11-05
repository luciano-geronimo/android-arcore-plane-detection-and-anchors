package arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.activities

import android.opengl.GLSurfaceView
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.R
import kotlinx.android.synthetic.main.activity_main.*

import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.helpers.DisplayRotationHelper
import arcorescenewithopengl.arcore.geronimo.don.arcoreusingopengl.common.helpers.TapHelper
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onDrawFrame(gl: GL10?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private lateinit var displayRotationHelper : DisplayRotationHelper
    private lateinit var tapHelper : TapHelper
    private var installRequested : Boolean = false
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
}
