//========================================================================
//$Id: JspcMojo.java 1410 2006-12-15 11:33:13Z janb $
//Copyright 2006 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package com.reucon.maven.plugin.openfire.jspc;

import org.apache.jasper.JspC;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.mortbay.jetty.webapp.WebAppClassLoader;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.util.IO;

import java.io.*;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

/**
 * This goal will compile jsps for an Openfire Plugin so that they can be included in the jar.
 *
 * @author janb
 * @goal jspc
 * @phase process-classes
 * @requiresDependencyResolution compile
 * @description Runs jspc compiler to produce .java and .class files
 */
public class JspcMojo extends AbstractMojo
{
    public static final String END_OF_WEBAPP = "</web-app>";

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;


    /**
     * File into which to generate the &lt;servlet&gt; and &lt;servlet-mapping&gt;
     * tags for the compiled jsps
     *
     * @parameter expression="${basedir}/target/webfrag.xml"
     */
    private String webXmlFragment;


    /**
     * Optional. A marker string in the src web.xml file which indicates where
     * to merge in the generated web.xml fragment. Note that the marker string
     * will NOT be preserved during the insertion. Can be left blank, in which
     * case the generated fragment is inserted just before the &lt;/web-app&gt; line
     *
     * @parameter
     */
    private String insertionMarker;

    /**
     * Merge the generated fragment file with the web.xml from
     * webAppSourceDirectory. The merged file will go into the same
     * directory as the webXmlFragment.
     *
     * @parameter expression="true"
     */
    private boolean mergeFragment;

    /**
     * The destination directory into which to put the
     * compiled jsps.
     *
     * @parameter expression="${basedir}/target/classes"
     */
    private String generatedClasses;


    /**
     * Controls whether or not .java files generated during compilation will be preserved.
     *
     * @parameter expression="false"
     */
    private boolean keepSources;


    /**
     * Default root package for all generated JSP classes.
     *
     * @parameter expression="${project.groupId}.jsp"
     */
    private String jspPackageRoot;

    /**
     * Root directory for all html/jsp etc files.
     *
     * @parameter expression="${basedir}/src/main/webapp"
     * @required
     */
    private String webAppSourceDirectory;


    /**
     * The location of the compiled classes for the webapp.
     *
     * @parameter expression="${project.build.outputDirectory}"
     */
    private File classesDirectory;

    /**
     * Whether or not to output more verbose messages during compilation.
     *
     * @parameter expression="false";
     */
    private boolean verbose;


    /**
     * If true, validates tlds when parsing.
     *
     * @parameter expression="false";
     */
    private boolean validateXml;


    /**
     * The encoding scheme to use.
     *
     * @parameter expression="UTF-8"
     */
    private String javaEncoding;


    /**
     * Whether or not to generate JSR45 compliant debug info
     *
     * @parameter expression="true";
     */
    private boolean suppressSmap;


    /**
     * Whether or not to ignore precompilation errors caused by
     * jsp fragments.
     *
     * @parameter expression="false"
     */
    private boolean ignoreJspFragmentErrors;


    /**
     * Allows a prefix to be appended to the standard schema locations
     * so that they can be loaded from elsewhere.
     *
     * @parameter
     */
    private String schemaResourcePrefix;


    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if (getLog().isDebugEnabled())
        {
            getLog().info("verbose=" + verbose);
            getLog().info("webAppSourceDirectory=" + webAppSourceDirectory);
            getLog().info("generatedClasses=" + generatedClasses);
            getLog().info("webXmlFragment=" + webXmlFragment);
            getLog().info("validateXml=" + validateXml);
            getLog().info("jspPackageRoot=" + jspPackageRoot);
            getLog().info("javaEncoding=" + javaEncoding);
            getLog().info("insertionMarker=" + (insertionMarker == null || insertionMarker.equals("") ? END_OF_WEBAPP : insertionMarker));
            getLog().info("keepSources=" + keepSources);
            getLog().info("mergeFragment=" + mergeFragment);
            getLog().info("suppressSmap=" + suppressSmap);
            getLog().info("ignoreJspFragmentErrors=" + ignoreJspFragmentErrors);
            getLog().info("schemaResourcePrefix=" + schemaResourcePrefix);
        }
        try
        {
            prepare();
            compile();
            cleanupSrcs();
            mergeWebXml();
        }
        catch (Exception e)
        {
            throw new MojoFailureException(e, "Failure processing jsps", "Failure processing jsps");
        }
    }


    public void compile()
            throws Exception
    {
        ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

        WebAppContext webAppContext = new WebAppContext();
        webAppContext.setContextPath("/");
        webAppContext.setWar(webAppSourceDirectory);

        WebAppClassLoader webAppClassLoader = new WebAppClassLoader(currentClassLoader, webAppContext);
        setUpClassPath(webAppClassLoader);
        StringBuffer classpathStr = new StringBuffer();
        URL[] urls = webAppClassLoader.getURLs();
        for (int i = 0; i < urls.length; i++)
        {
            if (getLog().isDebugEnabled())
            {
                getLog().debug("webappclassloader contains: " + urls[i]);
            }
            classpathStr.append(urls[i].getFile());
            if (getLog().isDebugEnabled())
            {
                getLog().debug("added to classpath: " + urls[i].getFile());
            }
            classpathStr.append(System.getProperty("path.separator"));
        }

        Thread.currentThread().setContextClassLoader(webAppClassLoader);

        JspC jspc = new JspC();
        jspc.setWebXmlFragment(webXmlFragment);
        jspc.setUriroot(webAppSourceDirectory);

        jspc.setPackage(jspPackageRoot);
        jspc.setOutputDir(generatedClasses);
        jspc.setValidateXml(validateXml);
        jspc.setClassPath(classpathStr.toString());
        jspc.setCompile(true);
        jspc.setSmapSuppressed(suppressSmap);
        jspc.setSmapDumped(!suppressSmap);
        jspc.setJavaEncoding(javaEncoding);

        //Glassfish jspc only checks

        try
        {
            jspc.setIgnoreJspFragmentErrors(ignoreJspFragmentErrors);
        }
        catch (NoSuchMethodError e)
        {
            getLog().debug("Tomcat Jasper does not support configuration option 'ignoreJspFragmentErrors': ignored");
        }

        try
        {
            if (schemaResourcePrefix != null)
            {
                jspc.setSchemaResourcePrefix(schemaResourcePrefix);
            }
        }
        catch (NoSuchMethodError e)
        {
            getLog().debug("Tomcat Jasper does not support configuration option 'schemaResourcePrefix': ignored");
        }
        if (verbose)
        {
            jspc.setVerbose(99);
        }
        else
        {
            jspc.setVerbose(0);
        }
        jspc.execute();

        Thread.currentThread().setContextClassLoader(currentClassLoader);
    }


    /**
     * Until Jasper supports the option to generate the srcs in a
     * different dir than the classes, this is the best we can do.
     *
     * @throws Exception
     */
    public void cleanupSrcs()
            throws Exception
    {
        //delete the .java files - depending on keepGenerated setting
        if (!keepSources)
        {
            File generatedClassesDir = new File(generatedClasses);
            File[] srcFiles = generatedClassesDir.listFiles(new FilenameFilter()
            {
                public boolean accept(File dir, String name)
                {
                    if (name == null)
                    {
                        return false;
                    }
                    if (name.trim().equals(""))
                    {
                        return false;
                    }
                    if (name.endsWith(".java"))
                    {
                        return true;
                    }

                    return false;
                }
            });

            for (int i = 0; (srcFiles != null) && (i < srcFiles.length); i++)
            {
                srcFiles[i].delete();
            }
        }
    }


    /**
     * Take the web fragment and put it inside a copy of the
     * web.xml file from the webAppSourceDirectory.
     * <p/>
     * You can specify the insertion point by specifying
     * the string in the insertionMarker configuration entry.
     * <p/>
     * If you dont specify the insertionMarker, then the fragment
     * will be inserted at the end of the file just before the
     * &lt;/webapp&gt;
     *
     * @throws Exception
     */
    public void mergeWebXml()
            throws Exception
    {
        if (mergeFragment)
        {
            BufferedReader webXmlReader = null;
            String marker = null;

            //open the src web.xml
            File webXml = new File(webAppSourceDirectory + "/WEB-INF/web.xml");

            File fragmentWebXml = new File(webXmlFragment);
            if (!fragmentWebXml.exists())
            {
                getLog().info("No fragment web.xml file generated");
            }
            File mergedWebXml = new File(fragmentWebXml.getParentFile(), "web.xml");
            PrintWriter mergedWebXmlWriter = new PrintWriter(new FileWriter(mergedWebXml));

            if (webXml.exists())
            {
                webXmlReader = new BufferedReader(new FileReader(webXml));
            }

            if (webXmlReader != null)
            {
                //read up to the insertion marker or the </webapp> if there is no marker
                boolean atInsertPoint = false;
                boolean atEOF = false;
                marker = (insertionMarker == null || insertionMarker.equals("") ? END_OF_WEBAPP : insertionMarker);
                while (!atInsertPoint && !atEOF)
                {
                    String line = webXmlReader.readLine();
                    if (line == null)
                    {
                        atEOF = true;
                    }
                    else if (line.indexOf(marker) >= 0)
                    {
                        atInsertPoint = true;
                    }
                    else
                    {
                        mergedWebXmlWriter.println(line);
                    }
                }
            }
            else
            {
                mergedWebXmlWriter.append("<web-app>\n");
            }

            //put in the generated fragment     
            BufferedReader fragmentWebXmlReader = new BufferedReader(new FileReader(fragmentWebXml));
            IO.copy(fragmentWebXmlReader, mergedWebXmlWriter);

            if (webXmlReader != null)
            {
                // if we inserted just before the </web-app>, put it back in
                if (END_OF_WEBAPP.equals(marker))
                {
                    mergedWebXmlWriter.println(END_OF_WEBAPP);
                }                                       

                // copy in the rest of the original web.xml file
                IO.copy(webXmlReader, mergedWebXmlWriter);

                webXmlReader.close();
            }
            else
            {
                mergedWebXmlWriter.append("\n</web-app>\n");
            }
            
            mergedWebXmlWriter.close();
            fragmentWebXmlReader.close();
        }
    }


    private void prepare()
            throws Exception
    {
        //For some reason JspC doesn't like it if the dir doesn't
        //already exist and refuses to create the web.xml fragment
        File generatedSourceDirectoryFile = new File(generatedClasses);
        if (!generatedSourceDirectoryFile.exists())
        {
            generatedSourceDirectoryFile.mkdirs();
        }
    }


    /**
     * Set up the execution classpath for Jasper.
     * <p/>
     * Put everything in the classesDirectory and all
     * of the dependencies on the classpath.
     *
     * @param classLoader we use a Jetty WebAppClassLoader to load the classes
     * @throws Exception
     */
    private void setUpClassPath(WebAppClassLoader classLoader) throws Exception
    {
        String classesDir = classesDirectory.getCanonicalPath();
        classesDir = classesDir + (classesDir.endsWith(File.pathSeparator) ? "" : File.separator);
        classLoader.addClassPath(classesDir);
        if (getLog().isDebugEnabled())
        {
            getLog().debug("Adding to classpath classes dir: " + classesDir);
        }

        for (Iterator iter = project.getArtifacts().iterator(); iter.hasNext();)
        {
            Artifact artifact = (Artifact) iter.next();
            String filePath = artifact.getFile().getCanonicalPath();

            if (!Artifact.SCOPE_TEST.equals(artifact.getScope()))
            {
                if (getLog().isDebugEnabled())
                {
                    getLog().debug("Adding to classpath dependency file: " + filePath);
                }

                classLoader.addClassPath(filePath);
            }
        }
    }
}
