package com.alibaba.datax.plugin.reader.obsreader;

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.util.RetryUtil;
import com.obs.services.ObsClient;
import com.obs.services.model.GetObjectRequest;
import com.obs.services.model.ObsObject;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

/**
 * @Author: guxuan
 * @Date 2022-05-17 15:52
 */
public class ObsInputStream extends InputStream {

    private final ObsClient obsClient;
    private GetObjectRequest getObjectRequest;

    private long startIndex = 0;
    private long endIndex = -1;

    private InputStream inputStream;

    /**
     * retryTimes : 重试次数, 默认值是60次;
     * description: 能够cover住的网络断连时间= retryTimes*(socket_timeout+sleepTime);
     *              默认cover住的网络断连时间= 60*(5+5) = 600秒.
     */
    private int retryTimes = 60;

    private static final Logger LOG = LoggerFactory.getLogger(ObsInputStream.class);

    /**
     * 如果start为0, end为1000, inputstream范围是[0,1000],共1001个字节
     *
     * @param obsClient
     * @param bucket
     * @param object
     * @param start inputstream start index
     * @param end inputstream end index
     */
    public ObsInputStream(final ObsClient obsClient, final String bucket, final String object, long start, long end) {
        this.obsClient = obsClient;
        this.getObjectRequest = new GetObjectRequest(bucket, object);
        this.startIndex = start;
        this.getObjectRequest.setRangeStart(this.startIndex);
        if (end - 0 >= 0) {
            this.getObjectRequest.setRangeEnd(end);
        } else {
            this.getObjectRequest.setRangeEnd(null);
        }
        this.endIndex = end;
        try {
            RetryUtil.executeWithRetry(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    ObsObject obsObject = obsClient.getObject(getObjectRequest);
                    // 读取InputStream
                    inputStream = obsObject.getObjectContent();
                    return true;
                }
            }, this.retryTimes, 5000, false);
        } catch (Exception e) {
            throw DataXException.asDataXException(
                    ObsReaderErrorCode.RUNTIME_EXCEPTION,e.getMessage(), e);
        }
    }

    public ObsInputStream(final ObsClient obsClient, final String bucket, final String object) {
        this.obsClient = obsClient;
        this.getObjectRequest = new GetObjectRequest(bucket, object);
        this.getObjectRequest.setRangeStart(startIndex);
        this.getObjectRequest.setRangeEnd((long)-1);
        try {
            RetryUtil.executeWithRetry(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    ObsObject obsObject = obsClient.getObject(getObjectRequest);
                    // 读取InputStream
                    inputStream = obsObject.getObjectContent();
                    return true;
                }
            }, this.retryTimes, 5000, false);
        } catch (Exception e) {
            throw DataXException.asDataXException(
                    ObsReaderErrorCode.RUNTIME_EXCEPTION, e.getMessage(), e);
        }
    }

    @Override
    public int read() throws IOException {
        int cbyte;
        try {
            cbyte = RetryUtil.executeWithRetry(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    try {
                        int c = inputStream.read();
                        startIndex++;
                        return c;
                    } catch (Exception e) {
                        LOG.warn(e.getMessage(),e);
                        /**
                         * 必须将inputStream先关闭, 否则会造成连接泄漏
                         */
                        IOUtils.closeQuietly(inputStream);
                        // getObsRangeInuptStream时,如果网络不连通,则会抛出异常,RetryUtil捕获异常进行重试
                        inputStream = getObsRangeInuptStream(startIndex);
                        int c = inputStream.read();
                        startIndex++;
                        return c;
                    }
                }
            }, this.retryTimes,5000, false);
            return cbyte;
        } catch (Exception e) {
            throw DataXException.asDataXException(
                    ObsReaderErrorCode.RUNTIME_EXCEPTION, e.getMessage(), e);
        }
    }

    private InputStream getObsRangeInuptStream(final long startIndex) {
        LOG.info("Start to retry reading [inputStream] from Byte {}", startIndex);
        // 第二个参数值设为-1，表示不设置结束的字节位置,读取startIndex及其以后的所有数据
        getObjectRequest.setRangeStart(startIndex);
        getObjectRequest.setRangeEnd(this.endIndex);
        // 范围下载
        ObsObject obsObject = obsClient.getObject(getObjectRequest);
        // 读取InputStream
        LOG.info("Start to retry reading [inputStream] from Byte {}", startIndex);
        return obsObject.getObjectContent();
    }
}
