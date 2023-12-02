package org.autojs.autojs.inrt.launch

import android.annotation.SuppressLint
import org.autojs.autojs.app.GlobalAppContext

/**
 * Created by Stardust on Mar 21, 2018.
 */
@SuppressLint("StaticFieldLeak")
object GlobalProjectLauncher: AssetsProjectLauncher("project", GlobalAppContext.get())
