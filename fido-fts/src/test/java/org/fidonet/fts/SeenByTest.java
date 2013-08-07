/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
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
public class SeenByTest {


    @Test
    public void testSortedSeenByToString() {
        String pathString = "467/1313 5020/545 1042 4441 5030/100 102 5040/102 5050/103 5060/545 580 5070/69 70 80 5080/1042";
        SeenBy actualPath = new SeenBy();
        actualPath.add(FTNAddr.valueOf("2:5060/545"));
        actualPath.add(FTNAddr.valueOf("2:5060/580"));
        actualPath.add(FTNAddr.valueOf("2:5020/545"));
        actualPath.add(FTNAddr.valueOf("2:5020/4441"));
        actualPath.add(FTNAddr.valueOf("2:5080/1042"));
        actualPath.add(FTNAddr.valueOf("2:5020/1042"));
        actualPath.add(FTNAddr.valueOf("2:5070/70"));
        actualPath.add(FTNAddr.valueOf("2:5070/69"));
        actualPath.add(FTNAddr.valueOf("2:5070/80"));
        actualPath.add(FTNAddr.valueOf("2:5030/100"));
        actualPath.add(FTNAddr.valueOf("2:5030/102"));
        actualPath.add(FTNAddr.valueOf("2:5050/103"));
        actualPath.add(FTNAddr.valueOf("2:5040/102"));
        actualPath.add(FTNAddr.valueOf("2:467/1313"));
        TestCase.assertEquals(pathString, actualPath.toString());
    }

    @Test
    public void testSortedSeenByToMessageString() {
        String pathString = "SEEN-BY: 467/60 68 100 113 150 780 1313 5020/545 1042 4441 5030/100 102\n" +
                "SEEN-BY: 5040/102 5050/103 5060/545 580 5070/69 70 80 5080/1042\n";
        SeenBy actualPath = new SeenBy();
        actualPath.add(FTNAddr.valueOf("2:5060/545"));
        actualPath.add(FTNAddr.valueOf("2:5060/580"));
        actualPath.add(FTNAddr.valueOf("2:5020/545"));
        actualPath.add(FTNAddr.valueOf("2:5020/4441"));
        actualPath.add(FTNAddr.valueOf("2:5080/1042"));
        actualPath.add(FTNAddr.valueOf("2:5020/1042"));
        actualPath.add(FTNAddr.valueOf("2:5070/70"));
        actualPath.add(FTNAddr.valueOf("2:5070/69"));
        actualPath.add(FTNAddr.valueOf("2:5070/80"));
        actualPath.add(FTNAddr.valueOf("2:5030/100"));
        actualPath.add(FTNAddr.valueOf("2:5030/102"));
        actualPath.add(FTNAddr.valueOf("2:5050/103"));
        actualPath.add(FTNAddr.valueOf("2:5040/102"));
        actualPath.add(FTNAddr.valueOf("2:467/1313"));
        actualPath.add(FTNAddr.valueOf("2:467/60"));
        actualPath.add(FTNAddr.valueOf("2:467/113"));
        actualPath.add(FTNAddr.valueOf("2:467/100"));
        actualPath.add(FTNAddr.valueOf("2:467/150"));
        actualPath.add(FTNAddr.valueOf("2:467/68"));
        actualPath.add(FTNAddr.valueOf("2:467/780"));
        actualPath.add(FTNAddr.valueOf("2:467/1313"));
        TestCase.assertEquals(pathString, actualPath.toSeenByString());
    }

    @Test
    public void testSortedSeenByToMessageStrings() {
        List<String> pathString = Arrays.asList("SEEN-BY: 467/60 68 100 113 150 780 1313 5020/545 1042 4441 5030/100 102",
                "SEEN-BY: 5040/102 5050/103 5060/545 580 5070/69 70 80 5080/1042");
        SeenBy actualPath = new SeenBy();
        actualPath.add(FTNAddr.valueOf("2:5060/545"));
        actualPath.add(FTNAddr.valueOf("2:5060/580"));
        actualPath.add(FTNAddr.valueOf("2:5020/545"));
        actualPath.add(FTNAddr.valueOf("2:5020/4441"));
        actualPath.add(FTNAddr.valueOf("2:5080/1042"));
        actualPath.add(FTNAddr.valueOf("2:5020/1042"));
        actualPath.add(FTNAddr.valueOf("2:5070/70"));
        actualPath.add(FTNAddr.valueOf("2:5070/69"));
        actualPath.add(FTNAddr.valueOf("2:5070/80"));
        actualPath.add(FTNAddr.valueOf("2:5030/100"));
        actualPath.add(FTNAddr.valueOf("2:5030/102"));
        actualPath.add(FTNAddr.valueOf("2:5050/103"));
        actualPath.add(FTNAddr.valueOf("2:5040/102"));
        actualPath.add(FTNAddr.valueOf("2:467/1313"));
        actualPath.add(FTNAddr.valueOf("2:467/60"));
        actualPath.add(FTNAddr.valueOf("2:467/113"));
        actualPath.add(FTNAddr.valueOf("2:467/100"));
        actualPath.add(FTNAddr.valueOf("2:467/150"));
        actualPath.add(FTNAddr.valueOf("2:467/68"));
        actualPath.add(FTNAddr.valueOf("2:467/780"));
        actualPath.add(FTNAddr.valueOf("2:467/1313"));
        TestCase.assertEquals(pathString, Arrays.asList(actualPath.toSeenByStrings()));
    }
}
