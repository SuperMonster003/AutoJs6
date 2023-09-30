package org.autojs.autojs.inrt.launch

import android.annotation.SuppressLint
import org.autojs.autojs.app.GlobalAppContext

/**
 * Created by Stardust on 2018/3/21.
 */
@SuppressLint("StaticFieldLeak")
object GlobalProjectLauncher: AssetsProjectLauncher("project", GlobalAppContext.get())
