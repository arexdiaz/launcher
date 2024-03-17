/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.launcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Bootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
class JvmLauncher
{
	private static final Logger logger = LoggerFactory.getLogger(JvmLauncher.class);

	private static String getJava() throws FileNotFoundException
	{
		Path javaHome = Paths.get(System.getProperty("java.home"));

		if (!Files.exists(javaHome))
		{
			throw new FileNotFoundException("JAVA_HOME is not set correctly! directory \"" + javaHome + "\" does not exist.");
		}

		Path javaPath = Paths.get(javaHome.toString(), "bin", "java.exe");

		if (!Files.exists(javaPath))
		{
			javaPath = Paths.get(javaHome.toString(), "bin", "java");
		}

		if (!Files.exists(javaPath))
		{
			throw new FileNotFoundException("java executable not found in directory \"" + javaPath.getParent() + "\"");
		}

		return javaPath.toAbsolutePath().toString();
	}

	static void launch(
		Bootstrap bootstrap,
		String repo1,
		String repo2,
		Collection<String> clientArgs,
		Map<String, String> jvmProps,
		List<String> jvmArgs) throws IOException
	{
		StringBuilder classPath = new StringBuilder();
		File dir1 = new File(repo1);
		File dir2 = new File(repo2);
		File[] files1 = dir1.listFiles((d, name) -> name.endsWith(".jar"));
		File[] files2;

		// If files1 is empty, include all files from dir2
		if (files1 == null || files1.length == 0)
		{
			files2 = dir2.listFiles((d, name) -> name.endsWith(".jar"));
		}
		else
		{
			files2 = dir2.listFiles((d, name) -> name.endsWith(".jar") && !name.startsWith("runelite-api-1.10"));
		}

		List<File> files = new ArrayList<>();
		if (files1 != null)
		{
			files.addAll(Arrays.asList(files1));
		}
		if (files2 != null)
		{
			files.addAll(Arrays.asList(files2));
		}

		for (var f : files)
		{
			if (classPath.length() > 0)
			{
				classPath.append(File.pathSeparatorChar);
			}

			classPath.append(f.getAbsolutePath());
		}

		String javaExePath;
		try
		{
			javaExePath = getJava();
		}
		catch (FileNotFoundException ex)
		{
			logger.error("Unable to find java executable", ex);
			return;
		}

		List<String> arguments = new ArrayList<>();
		arguments.add(javaExePath);
		arguments.add("-ea");
		arguments.add("-cp");
		arguments.add(classPath.toString());

		String[] jvmArguments = getJvmArguments(bootstrap);
		if (jvmArguments != null)
		{
			arguments.addAll(Arrays.asList(jvmArguments));
		}
		for (Map.Entry<String, String> entry : jvmProps.entrySet())
		{
			arguments.add("-D" + entry.getKey() + "=" + entry.getValue());
		}
		arguments.addAll(jvmArgs);

		arguments.add(LauncherProperties.getMain());
		arguments.add("--developer-mode");
		arguments.addAll(clientArgs);

		logger.info("Running {}", arguments);

		ProcessBuilder builder = new ProcessBuilder(arguments.toArray(new String[0]));
		builder.inheritIO();
		Process process = builder.start();

		if (log.isDebugEnabled())
		{
			SplashScreen.stop();

			try
			{
				process.waitFor();
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
	}

	static String[] getJvmArguments(Bootstrap bootstrap)
	{
		if (Launcher.isJava17())
		{
			switch (OS.getOs())
			{
				case Windows:
					String[] args = bootstrap.getClientJvm17WindowsArguments();
					return args != null ? args : bootstrap.getClientJvm17Arguments();
				case MacOS:
					args = bootstrap.getClientJvm17MacArguments();
					return args != null ? args : bootstrap.getClientJvm17Arguments();
				default:
					return bootstrap.getClientJvm17Arguments();
			}
		}
		else
		{
			return bootstrap.getClientJvm9Arguments();
		}
	}
}
