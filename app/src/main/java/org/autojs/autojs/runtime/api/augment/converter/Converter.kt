package org.autojs.autojs.runtime.api.augment.converter

import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey

object Converter : Augmentable() {

    override val keys = listOf(AugmentableKey("cvt"))

}
