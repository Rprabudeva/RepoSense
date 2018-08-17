import java.nio.file.PathMatcher;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import reposense.git.CommitNotFoundException;
import reposense.system.CommandRunner;
    private static final String DIFF_FILE_CHUNK_SEPARATOR = "\ndiff --git a/.*\n";
    private static final String BINARY_FILE_SYMBOL = "\nBinary files ";
    private static final String SIMILAR_FILE_RENAMED_SYMBOL = "similarity index 100%\n";
    private static final String LINE_CHUNKS_SEPARATOR = "\n@@ ";
    private static final String LINE_INSERTED_SYMBOL = "+";
    private static final String STARTING_LINE_NUMBER_GROUP_NAME = "startingLineNumber";
    private static final String FILE_CHANGED_GROUP_NAME = "filePath";
    private static final String MATCH_GROUP_FAIL_MESSAGE_FORMAT = "Failed to match the %s group for:\n%s";

    private static final int LINE_CHANGED_HEADER_INDEX = 0;

    private static final Pattern STARTING_LINE_NUMBER_PATTERN = Pattern.compile(
            "-(\\d)+(,)?(\\d)* \\+(?<startingLineNumber>\\d+)(,)?(\\d)* @@");
    private static final Pattern FILE_CHANGED_PATTERN = Pattern.compile("\n(\\+){3} b/(?<filePath>.*)\n");

    private static final Predicate<String> FILE_DELETED_PREDICATE = Pattern.compile(
            "^deleted file mode [\\d]{6}\n").asPredicate();
    private static final Predicate<String> NEW_EMPTY_FILE_PREDICATE = Pattern.compile(
            "^new file mode [a-zA-Z0-9\n. ]*$").asPredicate();

        logger.info("Extracting relevant file infos " + config.getLocation() + "...");

        List<FileInfo> fileInfos = new ArrayList<>();
        try {
            GitChecker.checkoutToDate(config.getRepoRoot(), config.getBranch(), config.getUntilDate());
        } catch (CommitNotFoundException cnfe) {
            return fileInfos;
        }
        String lastCommitHash = CommandRunner.getCommitHashBeforeDate(
                config.getRepoRoot(), config.getBranch(), config.getSinceDate());

        if (!lastCommitHash.isEmpty()) {
            fileInfos = getEditedFileInfos(config, lastCommitHash);
        } else {
            getAllFileInfo(config, Paths.get(config.getRepoRoot()), fileInfos);
        }

        return fileInfos;
    }

    /**
     * Generates a list of relevant {@code FileInfo} for all files that were edited in between the current
     * commit and the {@code lastCommitHash} commit, marks each {@code LineInfo} for each {@code FileInfo} on
     * whether they have been inserted within the commit range or not, and returns it.
     */
    public static List<FileInfo> getEditedFileInfos(RepoConfiguration config, String lastCommitHash) {
        List<FileInfo> fileInfos = new ArrayList<>();
        String fullDiffResult = CommandRunner.diffCommit(config.getRepoRoot(), lastCommitHash);

        // no diff between the 2 commits, return an empty list
        if (fullDiffResult.isEmpty()) {
            return fileInfos;
        }

        String[] fileDiffResultList = fullDiffResult.split(DIFF_FILE_CHUNK_SEPARATOR);

        for (String fileDiffResult : fileDiffResultList) {
            // file deleted, renamed, is binary file or is new and empty file, skip it
            if (fileDiffResult.contains(SIMILAR_FILE_RENAMED_SYMBOL) || fileDiffResult.contains(BINARY_FILE_SYMBOL)
                    || FILE_DELETED_PREDICATE.test(fileDiffResult) || NEW_EMPTY_FILE_PREDICATE.test(fileDiffResult)) {
                continue;
            }

            String filePath = getFilePathFromDiffPattern(fileDiffResult);

            if (isFormatInsideWhiteList(filePath, config.getFormats())) {
                FileInfo currentFileInfo = generateFileInfo(config.getRepoRoot(), filePath);
                setLinesToTrack(currentFileInfo, fileDiffResult);
                fileInfos.add(currentFileInfo);
            }
        }
    /**
     * Analyzes the {@code fileDiffResult} and marks each {@code LineInfo} in {@code FileInfo} on whether they were
     * inserted in between the commit range.
     */
    private static void setLinesToTrack(FileInfo fileInfo, String fileDiffResult) {
        String[] linesChangedChunk = fileDiffResult.split(LINE_CHUNKS_SEPARATOR);
        List<LineInfo> lineInfos = fileInfo.getLines();
        int fileLinePointer = 0;

        // skips the header, index starts from 1
        for (int sectionIndex = 1; sectionIndex < linesChangedChunk.length; sectionIndex++) {
            String linesChangedInSection = linesChangedChunk[sectionIndex];
            String[] linesChanged = linesChangedInSection.split("\n");
            int startingLineNumber = getStartingLineNumber(linesChanged[LINE_CHANGED_HEADER_INDEX]);

            // mark all untouched lines between sections as untracked
            while (fileLinePointer < startingLineNumber - 1) {
                lineInfos.get(fileLinePointer++).setTracked(false);
            }

            // skips the header, index starts from 1
            for (int lineIndex = 1; lineIndex < linesChanged.length; lineIndex++) {
                String lineChanged = linesChanged[lineIndex];
                // set line added to be tracked
                if (lineChanged.startsWith(LINE_INSERTED_SYMBOL)) {
                    lineInfos.get(fileLinePointer++).setTracked(true);
                }
            }
        }

        // set all remaining lines in file that were untouched to be untracked
        while (fileLinePointer < lineInfos.size()) {
            lineInfos.get(fileLinePointer++).setTracked(false);
        }
    }

    private static void getAllFileInfo(RepoConfiguration config, Path directory, List<FileInfo> fileInfos) {
                if (isFormatInsideWhiteList(relativePath, config.getFormats())) {
                    fileInfos.add(generateFileInfo(config.getRepoRoot(), relativePath));
        FileInfo fileInfo = new FileInfo(relativePath.replace('\\', '/'));
        Path path = Paths.get(repoRoot, fileInfo.getPath());

     * Returns true if {@code ignoreGlobMatcher} matchers the file path at {@code name}.
     */
    private static boolean shouldIgnore(String name, PathMatcher ignoreGlobMatcher) {
        return ignoreGlobMatcher.matches(Paths.get(name));
    }

    /**
     * Returns true if the {@code relativePath}'s file type is inside {@code formatsWhiteList}.
    private static boolean isFormatInsideWhiteList(String relativePath, List<String> formatsWhiteList) {
        return formatsWhiteList.stream().anyMatch(format -> relativePath.endsWith("." + format));
     * Returns the file path by matching the pattern inside {@code fileDiffResult}.
    private static String getFilePathFromDiffPattern(String fileDiffResult) {
        Matcher filePathMatcher = FILE_CHANGED_PATTERN.matcher(fileDiffResult);

        if (!filePathMatcher.find()) {
            logger.severe(String.format(MATCH_GROUP_FAIL_MESSAGE_FORMAT, "file path", fileDiffResult));
            throw new AssertionError("Should not have error matching file path pattern inside file diff result!");
        }

        return filePathMatcher.group(FILE_CHANGED_GROUP_NAME);
    }

    /**
     * Returns the starting line changed number, within the file diff result, by matching the pattern inside
     * {@code linesChanged}.
     */
    private static int getStartingLineNumber(String linesChanged) {
        Matcher chunkHeaderMatcher = STARTING_LINE_NUMBER_PATTERN.matcher(linesChanged);

        if (!chunkHeaderMatcher.find()) {
            logger.severe(String.format(MATCH_GROUP_FAIL_MESSAGE_FORMAT, "line changed", linesChanged));
            throw new AssertionError("Should not have error matching line number pattern inside chunk header!");
        }

        return Integer.parseInt(chunkHeaderMatcher.group(STARTING_LINE_NUMBER_GROUP_NAME));