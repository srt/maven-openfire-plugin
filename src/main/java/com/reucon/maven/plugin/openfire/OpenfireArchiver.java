package com.reucon.maven.plugin.openfire;

import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.war.WarArchiver;

import java.io.File;

public class OpenfireArchiver extends JarArchiver
{
    public OpenfireArchiver()
    {
        super();
    }

    public void addLib(File fileName) throws ArchiverException
    {
        addDirectory(fileName.getParentFile(), "lib/", new String[]{fileName.getName()}, null);
    }

    public void addLibs(File directoryName, String[] includes, String[] excludes) throws ArchiverException
    {
        addDirectory(directoryName, "lib/", includes, excludes);
    }

    public void addClass(File fileName) throws ArchiverException
    {
        addDirectory(fileName.getParentFile(), "classes/", new String[]{fileName.getName()}, null);
    }

    public void addClasses(File directoryName, String[] includes, String[] excludes) throws ArchiverException
    {
        addDirectory(directoryName, "classes/", includes, excludes);
    }

    public void addWebXml(File fileName) throws ArchiverException
    {
        addDirectory(fileName.getParentFile(), "web/WEB-INF/", new String[]{fileName.getName()}, null);
    }
}
