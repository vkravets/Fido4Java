/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.fts;

import junit.framework.TestCase;
import org.fidonet.types.FTNAddr;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/5/13
 * Time: 1:01 PM
 */
public class FidoPathTest {

    @Test
    public void testPathFromString() {
        String pathString = "5020/545 4441 1042 5030/100 102 5040/102 5050/103";
        FidoPath expectedPath = new FidoPath();
        expectedPath.add(FTNAddr.valueOf("2:5020/545"));
        expectedPath.add(FTNAddr.valueOf("2:5020/4441"));
        expectedPath.add(FTNAddr.valueOf("2:5020/1042"));
        expectedPath.add(FTNAddr.valueOf("2:5030/100"));
        expectedPath.add(FTNAddr.valueOf("2:5030/102"));
        expectedPath.add(FTNAddr.valueOf("2:5040/102"));
        expectedPath.add(FTNAddr.valueOf("2:5050/103"));

        FidoPath actualPath = FidoPath.valueOf(pathString, 2);
        TestCase.assertEquals(expectedPath, actualPath);
    }

    @Test
    public void testPathToString() {
        String pathString = "5020/545 4441 1042 5030/100 102 5040/102 5050/103";
        FidoPath actualPath = new FidoPath();
        actualPath.add(FTNAddr.valueOf("2:5020/545"));
        actualPath.add(FTNAddr.valueOf("2:5020/4441"));
        actualPath.add(FTNAddr.valueOf("2:5020/1042"));
        actualPath.add(FTNAddr.valueOf("2:5030/100"));
        actualPath.add(FTNAddr.valueOf("2:5030/102"));
        actualPath.add(FTNAddr.valueOf("2:5040/102"));
        actualPath.add(FTNAddr.valueOf("2:5050/103"));

        TestCase.assertEquals(pathString, actualPath.toString());
    }

    @Test
    public void testPathToMessageString() {
        String pathString = "\001PATH: 5020/545 4441 1042 5030/100 102 5040/102 5050/103 90 100 130 5\r" +
                "\001PATH: 462/100 68768 7657324 53824823 467/1313 7070\r";
        FidoPath actualPath = new FidoPath();
        actualPath.add(FTNAddr.valueOf("2:5020/545"));
        actualPath.add(FTNAddr.valueOf("2:5020/4441"));
        actualPath.add(FTNAddr.valueOf("2:5020/1042"));
        actualPath.add(FTNAddr.valueOf("2:5030/100"));
        actualPath.add(FTNAddr.valueOf("2:5030/102"));
        actualPath.add(FTNAddr.valueOf("2:5040/102"));
        actualPath.add(FTNAddr.valueOf("2:5050/103"));
        actualPath.add(FTNAddr.valueOf("2:5050/90"));
        actualPath.add(FTNAddr.valueOf("2:5050/100"));
        actualPath.add(FTNAddr.valueOf("2:5050/130"));
        actualPath.add(FTNAddr.valueOf("2:5050/5"));
        actualPath.add(FTNAddr.valueOf("2:462/100"));
        actualPath.add(FTNAddr.valueOf("2:462/68768"));
        actualPath.add(FTNAddr.valueOf("2:462/7657324"));
        actualPath.add(FTNAddr.valueOf("2:462/53824823"));
        actualPath.add(FTNAddr.valueOf("2:467/1313"));
        actualPath.add(FTNAddr.valueOf("2:467/7070"));
        TestCase.assertEquals(pathString, actualPath.toPathString());
    }

    @Test
    public void testPathToMessageStrings() {
        List<String> pathString = Arrays.asList("\001PATH: 5020/545 4441 1042 5030/100 102 5040/102 5050/103 90 100 130 5",
                "\001PATH: 462/100 68768 7657324 53824823 467/1313 7070");
        FidoPath actualPath = new FidoPath();
        actualPath.add(FTNAddr.valueOf("2:5020/545"));
        actualPath.add(FTNAddr.valueOf("2:5020/4441"));
        actualPath.add(FTNAddr.valueOf("2:5020/1042"));
        actualPath.add(FTNAddr.valueOf("2:5030/100"));
        actualPath.add(FTNAddr.valueOf("2:5030/102"));
        actualPath.add(FTNAddr.valueOf("2:5040/102"));
        actualPath.add(FTNAddr.valueOf("2:5050/103"));
        actualPath.add(FTNAddr.valueOf("2:5050/90"));
        actualPath.add(FTNAddr.valueOf("2:5050/100"));
        actualPath.add(FTNAddr.valueOf("2:5050/130"));
        actualPath.add(FTNAddr.valueOf("2:5050/5"));
        actualPath.add(FTNAddr.valueOf("2:462/100"));
        actualPath.add(FTNAddr.valueOf("2:462/68768"));
        actualPath.add(FTNAddr.valueOf("2:462/7657324"));
        actualPath.add(FTNAddr.valueOf("2:462/53824823"));
        actualPath.add(FTNAddr.valueOf("2:467/1313"));
        actualPath.add(FTNAddr.valueOf("2:467/7070"));
        TestCase.assertEquals(pathString, Arrays.asList(actualPath.toPathStrings()));
    }
}
