package com.reucon.maven.plugin.openfire;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.util.*;

import java.io.*;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public abstract class AbstractOpenfireMojo extends AbstractMojo
{
    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The directory containing generated classes.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     * @readonly
     */
    private File classesDirectory;

    /**
     * The Jar archiver needed for archiving classes directory into jar file under WEB-INF/lib.
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#jar}"
     * @required
     */
    protected JarArchiver jarArchiver;

    /**
     * The directory where the Openfire Plugin is built.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}"
     * @required
     */
    private File openfirePluginDirectory;

    /**
     * Single directory for extra files to include in the WAR.
     *
     * @parameter expression="${basedir}/src/main/webapp"
     * @required
     */
    private File warSourceDirectory;

    /**
     * Single directory for Openfire Plugin configuration files like <tt>plugin.xml</tt>,
     * <tt>changelog.html</tt> and <tt>readme.html</tt>.
     *
     * @parameter expression="${basedir}/src/main/openfire"
     * @required
     */
    private File openfireSourceDirectory;

    /**
     * Single directory for Openfire Plugin database scripts.
     *
     * @parameter expression="${basedir}/src/main/database"
     * @required
     */
    private File databaseSourceDirectory;

    /**
     * The list of webResources we want to transfer.
     *
     * @parameter
     */
    private Resource[] webResources;

    /**
     * Filters (property files) to include during the interpolation of the pom.xml.
     *
     * @parameter expression="${project.build.filters}"
     */
    private List filters;

    /**
     * The path to the web.xml file to use, original default was ${maven.war.webxml}.
     *
     * @parameter expression="${basedir}/target/web.xml"
     */
    private File webXml;

    /**
     * The file name mapping to use to copy libraries and tlds. If no file mapping is
     * set (default) the file is copied with its standard name.
     *
     * @parameter
     * @since 2.0.3
     */
    private String outputFileNameMapping;

    private static final String WEB_INF = "WEB-INF";

    private static final String META_INF = "META-INF";

    private static final String[] DEFAULT_INCLUDES = {"**/**"};

    private static final String DEFAULT_FILE_NAME_MAPPING_CLASSIFIER =
            "${artifactId}-${version}-${classifier}.${extension}";

    private static final String DEFAULT_FILE_NAME_MAPPING = "${artifactId}-${version}.${extension}";

    /**
     * The comma separated list of tokens to include in the WAR.
     * Default is '**'.
     *
     * @parameter alias="includes"
     */
    private String warSourceIncludes = "**";

    /**
     * The comma separated list of tokens to exclude from the WAR.
     *
     * @parameter alias="excludes"
     */
    private String warSourceExcludes;

    /**
     * The maven archive configuration to use.
     *
     * @parameter
     */
    protected MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    private static final String[] EMPTY_STRING_ARRAY = {};

    public MavenProject getProject()
    {
        return project;
    }

    public void setProject(MavenProject project)
    {
        this.project = project;
    }

    public File getClassesDirectory()
    {
        return classesDirectory;
    }

    public void setClassesDirectory(File classesDirectory)
    {
        this.classesDirectory = classesDirectory;
    }

    public File getOpenfirePluginDirectory()
    {
        return openfirePluginDirectory;
    }

    public void setOpenfirePluginDirectory(File openfirePluginDirectory)
    {
        this.openfirePluginDirectory = openfirePluginDirectory;
    }

    public File getWarSourceDirectory()
    {
        return warSourceDirectory;
    }

    public void setWarSourceDirectory(File warSourceDirectory)
    {
        this.warSourceDirectory = warSourceDirectory;
    }

    public File getWebXml()
    {
        return webXml;
    }

    public void setWebXml(File webXml)
    {
        this.webXml = webXml;
    }

    public String getOutputFileNameMapping()
    {
        return outputFileNameMapping;
    }

    public void setOutputFileNameMapping(String outputFileNameMapping)
    {
        this.outputFileNameMapping = outputFileNameMapping;
    }

    /**
     * Returns a string array of the excludes to be used
     * when assembling/copying the war.
     *
     * @return an array of tokens to exclude
     */
    protected String[] getExcludes()
    {
        List<String> excludeList = new ArrayList<String>();
        if (StringUtils.isNotEmpty(warSourceExcludes))
        {
            excludeList.addAll(Arrays.asList(StringUtils.split(warSourceExcludes, ",")));
        }

        return excludeList.toArray(EMPTY_STRING_ARRAY);
    }

    /**
     * Returns a string array of the includes to be used
     * when assembling/copying the war.
     *
     * @return an array of tokens to include
     */
    protected String[] getIncludes()
    {
        return StringUtils.split(StringUtils.defaultString(warSourceIncludes), ",");
    }

    public void buildExplodedOpenfirePlugin(File openfireDirectory) throws MojoExecutionException, MojoFailureException
    {
        getLog().info("Exploding Openfire Plugin...");

        openfireDirectory.mkdirs();

        try
        {
            buildWebapp(project, openfireDirectory);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Could not explode Openfire Plugin...", e);
        }
    }

    private Map getBuildFilterProperties() throws MojoExecutionException
    {
        Map<Object, Object> filterProperties = new Properties();

        // System properties
        filterProperties.putAll(System.getProperties());

        // Project properties
        filterProperties.putAll(project.getProperties());
        filterProperties.put("openfire-plugin.build.date", new SimpleDateFormat("MM/dd/yyyy").format(new Date()));

        for (String filtersfile : (List<String>) filters)
        {
            try
            {
                Properties properties = PropertyUtils.loadPropertyFile(new File(filtersfile), true, true);

                filterProperties.putAll(properties);
            }
            catch (IOException e)
            {
                throw new MojoExecutionException("Error loading property file '" + filtersfile + "'", e);
            }
        }

        // can't putAll, as ReflectionProperties doesn't enumerate - so we make a composite map with the project variables as dominant
        return new CompositeMap(new ReflectionProperties(project), filterProperties);
    }

    /**
     * Copies webapp webResources from the specified directory.
     * <p/>
     * Note that the <tt>webXml</tt> parameter could be null and may
     * specify a file which is not named <tt>web.xml<tt>. If the file
     * exists, it will be copied to the <tt>META-INF</tt> directory and
     * renamed accordingly.
     *
     * @param resource         the resource to copy
     * @param webappDirectory  the target directory
     * @param filterProperties
     * @throws java.io.IOException if an error occured while copying webResources
     */
    public void copyResources(Resource resource, File webappDirectory, Map filterProperties) throws IOException
    {
        if (!resource.getDirectory().equals(webappDirectory.getPath()))
        {
            getLog().info("Copy webapp webResources to " + webappDirectory.getAbsolutePath());
            if (webappDirectory.exists())
            {
                String[] fileNames = getWarFiles(resource);
                String targetPath = (resource.getTargetPath() == null) ? "" : resource.getTargetPath();
                File destination = new File(webappDirectory, targetPath);
                for (String fileName : fileNames)
                {
                    if (resource.isFiltering())
                    {
                        copyFilteredFile(new File(resource.getDirectory(), fileName),
                                new File(destination, fileName), null, getFilterWrappers(),
                                filterProperties);
                    }
                    else
                    {
                        copyFileIfModified(new File(resource.getDirectory(), fileName),
                                new File(destination, fileName));
                    }
                }
            }
        }
    }

    /**
     * Copies webapp webResources from the specified directory.
     * <p/>
     * Note that the <tt>webXml</tt> parameter could be null and may
     * specify a file which is not named <tt>web.xml<tt>. If the file
     * exists, it will be copied to the <tt>META-INF</tt> directory and
     * renamed accordingly.
     *
     * @param sourceDirectory the source directory
     * @param webappDirectory the target directory
     * @throws java.io.IOException if an error occured while copying webResources
     */
    public void copyResources(File sourceDirectory, File webappDirectory) throws IOException
    {
        if (!sourceDirectory.equals(webappDirectory))
        {
            getLog().info("Copying webResources to " + webappDirectory.getAbsolutePath());
            if (warSourceDirectory.exists())
            {
                String[] fileNames = getWarFiles(sourceDirectory);
                for (String fileName : fileNames)
                {
                    copyFileIfModified(new File(sourceDirectory, fileName), new File(webappDirectory, fileName));
                }
            }
        }
    }

    private void copyOpenfirePluginConfiguration(File sourceDirectory, File openfirePluginDirectory,
                                                 Map filterProperties) throws IOException
    {
        if (!sourceDirectory.equals(openfirePluginDirectory))
        {
            getLog().info("Copying Openfire Plugin configuration to " + openfirePluginDirectory.getAbsolutePath());
            if (warSourceDirectory.exists())
            {
                String[] fileNames = getWarFiles(sourceDirectory);
                for (String fileName : fileNames)
                {
                    if (fileName.endsWith(".html") || fileName.endsWith(".xml"))
                    {
                        copyFilteredFile(new File(sourceDirectory, fileName),
                                new File(openfirePluginDirectory, fileName), null, getFilterWrappers(),
                                filterProperties);
                    }
                    else
                    {
                        copyFileIfModified(new File(sourceDirectory, fileName),
                                new File(openfirePluginDirectory, fileName));
                    }
                }
            }
        }
    }

    /**
     * Builds the Openfire Plugin for the specified project.
     * <p/>
     * Classes and libraries are copied to
     * <tt>openfirePluginDirectory</tt> during this phase.
     *
     * @param project                 the maven project
     * @param openfirePluginDirectory
     * @throws java.io.IOException if an error occured while building the webapp
     */
    public void buildWebapp(MavenProject project, File openfirePluginDirectory)
            throws MojoExecutionException, IOException, MojoFailureException
    {
        getLog().info("Assembling webapp " + project.getArtifactId() + " in " + openfirePluginDirectory);

        File webinfDir = new File(openfirePluginDirectory, "web" + File.separator + WEB_INF);
        webinfDir.mkdirs();

        File metainfDir = new File(openfirePluginDirectory, META_INF);
        metainfDir.mkdirs();

        final Map filterProperties = getBuildFilterProperties();
        final List<Resource> webResources = this.webResources != null ? Arrays.asList(this.webResources) : null;
        if (webResources != null && webResources.size() > 0)
        {
            for (Resource resource : webResources)
            {
                if (!(new File(resource.getDirectory())).isAbsolute())
                {
                    resource.setDirectory(project.getBasedir() + File.separator + resource.getDirectory());
                }
                copyResources(resource, new File(openfirePluginDirectory, "web"), filterProperties);
            }
        }

        copyResources(warSourceDirectory, new File(openfirePluginDirectory, "web"));
        copyOpenfirePluginConfiguration(openfireSourceDirectory, openfirePluginDirectory, filterProperties);
        if (databaseSourceDirectory.exists())
        {
            copyDirectoryStructureIfModified(databaseSourceDirectory, new File(openfirePluginDirectory, "database"));
        }

        if (webXml != null && StringUtils.isNotEmpty(webXml.getName()))
        {
            if (!webXml.exists())
            {
                throw new MojoFailureException("The specified web.xml file '" + webXml + "' does not exist");
            }

            //rename to web.xml
            copyFileIfModified(webXml, new File(webinfDir, "/web.xml"));
        }

        File libDirectory = new File(openfirePluginDirectory, "lib");
        File classesDirectory = new File(openfirePluginDirectory, "classes");

        if (this.classesDirectory.exists() && !this.classesDirectory.equals(classesDirectory))
        {
            copyDirectoryStructureIfModified(this.classesDirectory, classesDirectory);
        }

        Set<Artifact> artifacts = project.getArtifacts();
        List<String> duplicates = findDuplicates(artifacts);

        for (Artifact artifact : artifacts)
        {
            String targetFileName = getFinalName(artifact);

            getLog().debug("Processing: " + targetFileName);

            if (duplicates.contains(targetFileName))
            {
                getLog().debug("Duplicate found: " + targetFileName);
                targetFileName = artifact.getGroupId() + "-" + targetFileName;
                getLog().debug("Renamed to: " + targetFileName);
            }

            // TODO: utilise appropriate methods from project builder
            ScopeArtifactFilter filter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
            if (!artifact.isOptional() && filter.include(artifact))
            {
                String type = artifact.getType();
                if ("jar".equals(type) || "test-jar".equals(type))
                {
                    copyFileIfModified(artifact.getFile(), new File(libDirectory, targetFileName));
                }
                else
                {
                    getLog().debug("Skipping artifact of type " + type + " for WEB-INF/lib");
                }
            }
        }
    }

    /**
     * Searches a set of artifacts for duplicate filenames and returns a list of duplicates.
     *
     * @param artifacts set of artifacts
     * @return List of duplicated artifacts
     */
    private List<String> findDuplicates(Set<Artifact> artifacts)
    {
        List<String> duplicates = new ArrayList<String>();
        List<String> identifiers = new ArrayList<String>();
        for (Artifact artifact : artifacts)
        {
            String candidate = getFinalName(artifact);
            if (identifiers.contains(candidate))
            {
                duplicates.add(candidate);
            }
            else
            {
                identifiers.add(candidate);
            }
        }
        return duplicates;
    }

    /**
     * Returns a list of filenames that should be copied
     * over to the destination directory.
     *
     * @param sourceDir the directory to be scanned
     * @return the array of filenames, relative to the sourceDir
     */
    private String[] getWarFiles(File sourceDir)
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDir);
        scanner.setExcludes(getExcludes());
        scanner.addDefaultExcludes();

        scanner.setIncludes(getIncludes());

        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * Returns a list of filenames that should be copied
     * over to the destination directory.
     *
     * @param resource the resource to be scanned
     * @return the array of filenames, relative to the sourceDir
     */
    private String[] getWarFiles(Resource resource)
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(resource.getDirectory());
        if (resource.getIncludes() != null && !resource.getIncludes().isEmpty())
        {
            scanner.setIncludes((String[]) resource.getIncludes().toArray(EMPTY_STRING_ARRAY));
        }
        else
        {
            scanner.setIncludes(DEFAULT_INCLUDES);
        }
        if (resource.getExcludes() != null && !resource.getExcludes().isEmpty())
        {
            scanner.setExcludes((String[]) resource.getExcludes().toArray(EMPTY_STRING_ARRAY));
        }

        scanner.addDefaultExcludes();

        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * Copy file from source to destination only if source is newer than the target file.
     * If <code>destinationDirectory</code> does not exist, it
     * (and any parent directories) will be created. If a file <code>source</code> in
     * <code>destinationDirectory</code> exists, it will be overwritten.
     *
     * @param source               An existing <code>File</code> to copy.
     * @param destinationDirectory A directory to copy <code>source</code> into.
     * @throws java.io.FileNotFoundException if <code>source</code> isn't a normal file.
     * @throws IllegalArgumentException      if <code>destinationDirectory</code> isn't a directory.
     * @throws java.io.IOException           if <code>source</code> does not exist, the file in
     *                                       <code>destinationDirectory</code> cannot be written to, or an IO error occurs during copying.
     *                                       <p/>
     *                                       TO DO: Remove this method when Maven moves to plexus-utils version 1.4
     */
    private static void copyFileToDirectoryIfModified(File source, File destinationDirectory) throws IOException
    {
        // TO DO: Remove this method and use the method in WarFileUtils when Maven 2 changes
        // to plexus-utils 1.2.
        if (destinationDirectory.exists() && !destinationDirectory.isDirectory())
        {
            throw new IllegalArgumentException("Destination is not a directory");
        }

        copyFileIfModified(source, new File(destinationDirectory, source.getName()));
    }

    private FilterWrapper[] getFilterWrappers()
    {
        return new FilterWrapper[]{
                // support ${token}
                new FilterWrapper()
                {
                    public Reader getReader(Reader fileReader, Map filterProperties)
                    {
                        return new InterpolationFilterReader(fileReader, filterProperties, "${", "}");
                    }
                },
                // support @token@
                new FilterWrapper()
                {
                    public Reader getReader(Reader fileReader, Map filterProperties)
                    {
                        return new InterpolationFilterReader(fileReader, filterProperties, "@", "@");
                    }
                }};
    }

    /**
     * @param from
     * @param to
     * @param encoding
     * @param wrappers
     * @param filterProperties
     * @throws IOException TO DO: Remove this method when Maven moves to plexus-utils version 1.4
     */
    private static void copyFilteredFile(File from, File to, String encoding, FilterWrapper[] wrappers,
                                         Map filterProperties)
            throws IOException
    {
        // buffer so it isn't reading a byte at a time!
        Reader fileReader = null;
        Writer fileWriter = null;
        try
        {
            // fix for MWAR-36, ensures that the parent dir are created first
            to.getParentFile().mkdirs();

            if (encoding == null || encoding.length() < 1)
            {
                fileReader = new BufferedReader(new FileReader(from));
                fileWriter = new FileWriter(to);
            }
            else
            {
                FileInputStream instream = new FileInputStream(from);

                FileOutputStream outstream = new FileOutputStream(to);

                fileReader = new BufferedReader(new InputStreamReader(instream, encoding));

                fileWriter = new OutputStreamWriter(outstream, encoding);
            }

            Reader reader = fileReader;
            for (FilterWrapper wrapper : wrappers)
            {
                reader = wrapper.getReader(reader, filterProperties);
            }

            IOUtil.copy(reader, fileWriter);
        }
        finally
        {
            IOUtil.close(fileReader);
            IOUtil.close(fileWriter);
        }
    }

    /**
     * Copy file from source to destination only if source timestamp is later than the destination timestamp.
     * The directories up to <code>destination</code> will be created if they don't already exist.
     * <code>destination</code> will be overwritten if it already exists.
     *
     * @param source      An existing non-directory <code>File</code> to copy bytes from.
     * @param destination A non-directory <code>File</code> to write bytes to (possibly
     *                    overwriting).
     * @throws IOException                   if <code>source</code> does not exist, <code>destination</code> cannot be
     *                                       written to, or an IO error occurs during copying.
     * @throws java.io.FileNotFoundException if <code>destination</code> is a directory
     *                                       <p/>
     *                                       TO DO: Remove this method when Maven moves to plexus-utils version 1.4
     */
    private static void copyFileIfModified(File source, File destination)
            throws IOException
    {
        // TO DO: Remove this method and use the method in WarFileUtils when Maven 2 changes
        // to plexus-utils 1.2.
        if (destination.lastModified() < source.lastModified())
        {
            FileUtils.copyFile(source.getCanonicalFile(), destination);
            // preserve timestamp
            destination.setLastModified(source.lastModified());
        }
    }

    /**
     * Copies a entire directory structure but only source files with timestamp later than the destinations'.
     * <p/>
     * Note:
     * <ul>
     * <li>It will include empty directories.
     * <li>The <code>sourceDirectory</code> must exists.
     * </ul>
     *
     * @param sourceDirectory
     * @param destinationDirectory
     * @throws IOException TO DO: Remove this method when Maven moves to plexus-utils version 1.4
     */
    private static void copyDirectoryStructureIfModified(File sourceDirectory, File destinationDirectory)
            throws IOException
    {
        if (!sourceDirectory.exists())
        {
            throw new IOException("Source directory doesn't exists (" + sourceDirectory.getAbsolutePath() + ").");
        }

        File[] files = sourceDirectory.listFiles();

        String sourcePath = sourceDirectory.getAbsolutePath();

        for (File file : files)
        {
            String dest = file.getAbsolutePath();

            dest = dest.substring(sourcePath.length() + 1);

            File destination = new File(destinationDirectory, dest);

            if (file.isFile())
            {
                destination = destination.getParentFile();

                copyFileToDirectoryIfModified(file, destination);
            }
            else if (file.isDirectory())
            {
                if (!destination.exists() && !destination.mkdirs())
                {
                    throw new IOException(
                            "Could not create destination directory '" + destination.getAbsolutePath() + "'.");
                }

                copyDirectoryStructureIfModified(file, destination);
            }
            else
            {
                throw new IOException("Unknown file type: " + file.getAbsolutePath());
            }
        }
    }

    /**
     * TO DO: Remove this interface when Maven moves to plexus-utils version 1.4
     */
    private interface FilterWrapper
    {
        Reader getReader(Reader fileReader, Map filterProperties);
    }

    /**
     * Returns the final name of the specified artifact.
     * <p/>
     * If the <tt>outputFileNameMapping</tt> is set, it is used, otherwise
     * the standard naming scheme is used.
     *
     * @param artifact the artifact
     * @return the converted filename of the artifact
     */
    private String getFinalName(Artifact artifact)
    {
        if (outputFileNameMapping != null)
        {
            return MappingUtils.evaluateFileNameMapping(outputFileNameMapping, artifact);
        }

        String classifier = artifact.getClassifier();
        if ((classifier != null) && !("".equals(classifier.trim())))
        {
            return MappingUtils.evaluateFileNameMapping(DEFAULT_FILE_NAME_MAPPING_CLASSIFIER, artifact);
        }
        else
        {
            return MappingUtils.evaluateFileNameMapping(DEFAULT_FILE_NAME_MAPPING, artifact);
        }
    }
}
