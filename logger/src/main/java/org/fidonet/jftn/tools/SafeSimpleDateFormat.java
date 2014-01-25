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

package org.fidonet.jftn.tools;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 1/25/14
 * Time: 10:09 PM
 * <p/>
 * idea taken from com.cedarsoftware.util.SafeSimpleDateFormat
 * https://github.com/jdereg/java-util/blob/master/src/main/java/com/cedarsoftware/util/SafeSimpleDateFormat.java
 */
public class SafeSimpleDateFormat {


    private static final ThreadLocal<Map<DateFormatInfo, DateFormat>> safeDateFormatCache = new ThreadLocal<Map<DateFormatInfo, DateFormat>>() {
        @Override
        protected Map<DateFormatInfo, DateFormat> initialValue() {
            return new HashMap<DateFormatInfo, DateFormat>();
        }
    };

    private DateFormatInfo dfInfo;

    public SafeSimpleDateFormat() {
        this("dd.MM.yy HH:mm:ss.SSS", null);
    }

    public SafeSimpleDateFormat(String format) {
        this(format, null);
    }

    public SafeSimpleDateFormat(String format, Locale locale) {
        dfInfo = new DateFormatInfo(format, locale);
    }

    private DateFormat getFromCache(DateFormatInfo dfInfo) {
        Map<DateFormatInfo, DateFormat> cache = safeDateFormatCache.get();
        DateFormat dateFormat = cache.get(dfInfo);
        if (dateFormat == null) {
            dateFormat = new SimpleDateFormat(dfInfo.getFormat(), dfInfo.getLocale());
            cache.put(dfInfo, dateFormat);
        }
        return dateFormat;
    }

    public String format(Date date) {
        return getFromCache(dfInfo).format(date);
    }

    public Date parse(String date) throws ParseException {
        return getFromCache(dfInfo).parse(date);
    }

    private class DateFormatInfo {

        private String format;
        private Locale locale;

        public DateFormatInfo(String format, Locale locale) {
            this.format = format;
            this.locale = locale;
        }

        public String getFormat() {
            return format;
        }

        public Locale getLocale() {
            return locale;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DateFormatInfo that = (DateFormatInfo) o;

            if (!format.equals(that.format)) return false;
            if (locale != null ? !locale.equals(that.locale) : that.locale != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = format.hashCode();
            result = 31 * result + (locale != null ? locale.hashCode() : 0);
            return result;
        }
    }
}
