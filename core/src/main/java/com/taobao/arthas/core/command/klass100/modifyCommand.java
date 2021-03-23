package com.taobao.arthas.core.command.klass100;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;
import java.io.IOException;


import com.taobao.arthas.core.shell.command.AnnotatedCommand;
import com.taobao.arthas.core.shell.command.CommandProcess;
import com.alibaba.arthas.deps.org.slf4j.Logger;
import com.alibaba.deps.org.objectweb.asm.ClassReader;


abstract class ModifyCommand extends AnnotatedCommand {
    protected static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    protected void processPath(CommandProcess process, List<String> paths, Logger logger, Map<String, byte[]> bytesMap){
        for (String path : paths) {
            File file = new File(path);
            if (!file.exists()) {
                process.end(-1, "file does not exist, path:" + path);
                return;
            }
            if (!file.isFile()) {
                process.end(-1, "not a normal file, path:" + path);
                return;
            }
            if (file.length() >= MAX_FILE_SIZE) {
                process.end(-1, "file size: " + file.length() + " >= " + MAX_FILE_SIZE + ", path: " + path);
                return;
            }
        }
        for (String path : paths) {
            RandomAccessFile f = null;
            try {
                f = new RandomAccessFile(path, "r");
                final byte[] bytes = new byte[(int) f.length()];
                f.readFully(bytes);
                final String clazzName = readClassName(bytes);
                bytesMap.put(clazzName, bytes);
            } catch (Exception e) {
                logger.warn("load class file failed: "+path, e);
                process.end(-1, "load class file failed: " +path+", error: " + e);
                return;
            } finally {
                if (f != null) {
                    try {
                        f.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
        if (bytesMap.size() != paths.size()) {
            process.end(-1, "paths may contains same class name!");
            return;
        }
    }

    private static String readClassName(final byte[] bytes) {
        return new ClassReader(bytes).getClassName().replace('/', '.');
    }
}