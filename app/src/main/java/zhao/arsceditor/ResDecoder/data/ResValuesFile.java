/**
 * Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zhao.arsceditor.ResDecoder.data;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 * @see <a href="https://github.com/iBotPeaches/Apktool/blob/master/brut.apktool/apktool-lib/src/main/java/brut/androlib/res/data/ResValuesFile.java">Source code of Apktool on GitHub</a>
 */
public class ResValuesFile {
    private final ResType mConfig;
    private final ResPackage mPackage;
    private final Set<ResResource> mResources = new LinkedHashSet<ResResource>();
    private final ResTypeSpec mType;

    public ResValuesFile(ResPackage pkg, ResTypeSpec type, ResType config) {
        mPackage = pkg;
        mType = type;
        mConfig = config;
    }

    public void addResource(ResResource res) {
        mResources.add(res);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ResValuesFile other = (ResValuesFile) obj;
        if (!Objects.equals(mType, other.mType)) {
            return false;
        }
        return Objects.equals(mConfig, other.mConfig);
    }

    public ResType getConfig() {
        return mConfig;
    }

    public String getPath() {
        return "values" + mConfig.getFlags().getQualifiers() + "/" + mType.getName()
                + (mType.getName().endsWith("s") ? "" : "s") + ".xml";
    }

    public ResTypeSpec getType() {
        return mType;
    }

    @Override
    public int hashCode() {
		// @Hint by JetBrains AI Assistant on Oct 25, 2024.
		//  ! 17 is an arbitrarily chosen prime number as the initial value of the hash code.
		//  ! Choosing a prime number helps to avoid collisions in data distribution.
		//  ! 17 is just a common convention for hash code calculation and has no specific meaning.
		//  ! This pattern can ensure a good distribution of hash codes and provide better performance.
		//  ! 31 is also a prime number that acts as a mixer for hash code calculation.
		//  ! Multiplying by the prime number 31 helps to evenly distribute the hash values
		//  ! across different properties and reduce collisions.
		//  !
		//  ! zh-CN (translated by Jetbrains AI Assistant on Oct 25, 2024):
		//  !
		//  ! 17 是一个任意选择的质数, 作为哈希码的起始值.
		//  ! 选择质数有助于在数据分布上避免冲突.
		//  ! 17 只是哈希码计算的一种通用约定, 并没有具体的特殊含义.
		//  ! 这种模式可以确保哈希码的良好分布, 提供较好的性能.
		//  ! 31 也是一个质数, 它的作用类似于混合器, 用于哈希码的计算.
		//  ! 乘以质数 31 可以帮助在不同属性值上更均匀地分散哈希值, 减少碰撞.
        int hash = 17;
        hash = 31 * hash + (mType != null ? mType.hashCode() : 0);
        hash = 31 * hash + (mConfig != null ? mConfig.hashCode() : 0);
        return hash;
    }

    public boolean isSynthesized(ResResource res) {
        return mPackage.isSynthesized(res.getResSpec().getId());
    }

    public Set<ResResource> listResources() {
        return mResources;
    }
}
