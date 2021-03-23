package com.taobao.arthas.core.command.klass100;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import com.alibaba.arthas.deps.org.slf4j.Logger;
import com.alibaba.arthas.deps.org.slf4j.LoggerFactory;
import com.taobao.arthas.core.command.Constants;
import com.taobao.arthas.core.command.model.RedefineModel;
import com.taobao.arthas.core.command.model.ClassLoaderVO;
import com.taobao.arthas.core.shell.cli.Completion;
import com.taobao.arthas.core.shell.cli.CompletionUtils;
import com.taobao.arthas.core.shell.command.CommandProcess;
import com.taobao.arthas.core.util.ClassUtils;
import com.taobao.arthas.core.util.ClassLoaderUtils;
import com.taobao.middleware.cli.annotations.Argument;
import com.taobao.middleware.cli.annotations.Description;
import com.taobao.middleware.cli.annotations.Name;
import com.taobao.middleware.cli.annotations.Option;
import com.taobao.middleware.cli.annotations.Summary;

/**
 * Redefine Classes.
 *
 * @author hengyunabc 2018-07-13
 * @see java.lang.instrument.Instrumentation#redefineClasses(ClassDefinition...)
 */
@Name("redefine")
@Summary("Redefine classes. @see Instrumentation#redefineClasses(ClassDefinition...)")
@Description(Constants.EXAMPLE +
                "  redefine /tmp/Test.class\n" +
                "  redefine -c 327a647b /tmp/Test.class /tmp/Test\\$Inner.class \n" +
                "  redefine --classLoaderClass 'sun.misc.Launcher$AppClassLoader' /tmp/Test.class \n" +
                Constants.WIKI + Constants.WIKI_HOME + "redefine")
public class RedefineCommand extends ModifyCommand {
    private static final Logger logger = LoggerFactory.getLogger(RedefineCommand.class);
    

    private String hashCode;
    private String classLoaderClass;

    private List<String> paths;

    @Option(shortName = "c", longName = "classloader")
    @Description("classLoader hashcode")
    public void setHashCode(String hashCode) {
        this.hashCode = hashCode;
    }

    @Option(longName = "classLoaderClass")
    @Description("The class name of the special class's classLoader.")
    public void setClassLoaderClass(String classLoaderClass) {
        this.classLoaderClass = classLoaderClass;
    }

    @Argument(argName = "classfilePaths", index = 0)
    @Description(".class file paths")
    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    @Override
    public void process(CommandProcess process) {
        RedefineModel redefineModel = new RedefineModel();
        Instrumentation inst = process.session().getInstrumentation();
        
        Map<String, byte[]> bytesMap = new HashMap<String, byte[]>();
        processPath(process, paths, logger, bytesMap);

        List<ClassDefinition> definitions = new ArrayList<ClassDefinition>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (bytesMap.containsKey(clazz.getName())) {

                if (hashCode == null && classLoaderClass != null) {
                    List<ClassLoader> matchedClassLoaders = ClassLoaderUtils.getClassLoaderByClassName(inst, classLoaderClass);
                    if (matchedClassLoaders.size() == 1) {
                        hashCode = Integer.toHexString(matchedClassLoaders.get(0).hashCode());
                    } else if (matchedClassLoaders.size() > 1) {
                        Collection<ClassLoaderVO> classLoaderVOList = ClassUtils.createClassLoaderVOList(matchedClassLoaders);
                        RedefineModel classredefineModel = new RedefineModel()
                                .setClassLoaderClass(classLoaderClass)
                                .setMatchedClassLoaders(classLoaderVOList);
                        process.appendResult(classredefineModel);
                        process.end(-1, "Found more than one classloader by class name, please specify classloader with '-c <classloader hash>'");
                        return;
                    } else {
                        process.end(-1, "Can not find classloader by class name: " + classLoaderClass + ".");
                        return;
                    }
                }
                
                ClassLoader classLoader = clazz.getClassLoader();
                if (classLoader != null && hashCode != null && !Integer.toHexString(classLoader.hashCode()).equals(hashCode)) {
                    continue;
                }
                definitions.add(new ClassDefinition(clazz, bytesMap.get(clazz.getName())));
                redefineModel.addRedefineClass(clazz.getName());
                logger.info("Try redefine class name: {}, ClassLoader: {}", clazz.getName(), clazz.getClassLoader());
            }
        }

        try {
            if (definitions.isEmpty()) {
                process.end(-1, "These classes are not found in the JVM and may not be loaded: " + bytesMap.keySet());
                return;
            }
            inst.redefineClasses(definitions.toArray(new ClassDefinition[0]));
            process.appendResult(redefineModel);
            process.end();
        } catch (Throwable e) {
            String message = "redefine error! " + e.toString();
            logger.error(message, e);
            process.end(-1, message);
        }

    }

    @Override
    public void complete(Completion completion) {
        if (!CompletionUtils.completeFilePath(completion)) {
            super.complete(completion);
        }
    }
}
