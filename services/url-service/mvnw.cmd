@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------

@ECHO OFF
@SETLOCAL LEAVE_CONSOLE

@SET ERROR_CODE=0

@SET MAVEN_CMD_LINE_ARGS=%*

@WHERE mvn >nul 2>nul
@IF %ERRORLEVEL% EQU 0 (
  mvn %MAVEN_CMD_LINE_ARGS%
  EXIT /B %ERRORLEVEL%
)

@IF NOT "%JAVA_HOME%" == "" (
  SET "JAVACMD=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVACMD=java.exe"
)

@"%JAVACMD%" -version >nul 2>nul
@IF %ERRORLEVEL% NEQ 0 (
  ECHO ERROR: Java is not found in PATH or JAVA_HOME. >&2
  EXIT /B 1
)

ECHO Maven wrapper: falling back to system mvn or docker maven build.
mvn %MAVEN_CMD_LINE_ARGS%
EXIT /B %ERRORLEVEL%
