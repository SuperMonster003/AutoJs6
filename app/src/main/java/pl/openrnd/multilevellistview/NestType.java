/******************************************************************************
 *
 *  2016 (C) Copyright Open-RnD Sp. z o.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package pl.openrnd.multilevellistview;

/**
 * MultiLevelListView nest types.
 */
public enum NestType {

    /**
     * SINGLE nest type. Only one group item is expanded at the same time.
     */
    SINGLE(0),
    /**
     * MULTIPLE nest type. Any group items are expandnded at the same time.
     */
    MULTIPLE(1);

    private int mValue;

    /**
     * Constructor.
     *
     * @param value nest type value.
     */
    NestType(int value) {
        mValue = value;
    }

    /**
     * Gets nest type value.
     *
     * @return Nest type value.
     */
    public int getValue() {
        return mValue;
    }

    /**
     * Converts integer to nest type.
     *
     * @param value nest type as integer.
     * @return Nest type value.
     */
    public static NestType fromValue(int value) {
        switch (value) {
            case 0:
                return SINGLE;
            case 1:
            default:
                return MULTIPLE;
        }
    }
}
