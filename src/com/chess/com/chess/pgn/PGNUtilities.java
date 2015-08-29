package com.chess.com.chess.pgn;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.chess.com.chess.pgn.PGNGameTags.TagsBuilder;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.gui.Table.MoveLog;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class PGNUtilities {

    private static final Pattern PGN_PATTERN = Pattern.compile("\\[(\\w+)\\s+\"(.*?)\"\\]$");
    private static final Pattern KING_SIDE_CASTLE = Pattern.compile("O-O#?\\+?");
    private static final Pattern QUEEN_SIDE_CASTLE = Pattern.compile("O-O-O#?\\+?");
    private static final Pattern PLAIN_PAWN_MOVE = Pattern.compile("^([a-h][0-8])(\\+)?(#)?$");
    private static final Pattern PAWN_ATTACK_MOVE = Pattern.compile("(^[a-h])(x)([a-h][0-8])(\\+)?(#)?$");
    private static final Pattern PLAIN_MAJOR_MOVE = Pattern.compile("^(B|N|R|Q|K)([a-h]|[1-8])?([a-h][0-8])(\\+)?(#)?$");
    private static final Pattern MAJOR_ATTACK_MOVE = Pattern.compile("^(B|N|R|Q|K)([a-h]|[1-8])?(x)([a-h][0-8])(\\+)?(#)?$");
    private static final Pattern PLAIN_PAWN_PROMOTION_MOVE = Pattern.compile("(.*?)=(.*?)");
    private static final Pattern ATTACK_PAWN_PROMOTION_MOVE = Pattern.compile("(.*?)x(.*?)=(.*?)");

    public static void persistPGNFile(final File pgnFile) throws IOException {

        int count = 0;
        int validCount = 0;

        try (final BufferedReader br = new BufferedReader(new FileReader(pgnFile))) {
            String line;
            TagsBuilder tagsBuilder = new TagsBuilder();
            StringBuilder gameTextBuilder = new StringBuilder();
            while((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    if (isTag(line)) {
                        final Matcher matcher = PGN_PATTERN.matcher(line);
                        if (matcher.find()) {
                            tagsBuilder.addTag(matcher.group(1), matcher.group(2));
                        }
                    }
                    else if (isEndOfGame(line)) {
                        final String[] ending = line.split(" ");
                        final String outcome = ending[ending.length - 1];
                        gameTextBuilder.append(line.replace(outcome, "")).append(" ");
                        final String gameText = gameTextBuilder.toString().trim();
                        if(!gameText.isEmpty() && gameText.length() > 80) {
                            final Game game = GameFactory.createGame(tagsBuilder.build(), gameText, outcome);
                            System.out.println("(" +(++count)+") Finished parsing " +game+ " count = " + (++count));
                            if(game.isValid()) {
                                MySqlGamePersistence.get().persistGame(game);
                                validCount++;
                            }
                        }
                        gameTextBuilder = new StringBuilder();
                        tagsBuilder = new TagsBuilder();
                    }
                    else {
                        gameTextBuilder.append(line).append(" ");
                    }
                }
            }
            br.readLine();
        }
        System.out.println("Finished building book from pgn file: " + pgnFile + " Parsed " +count+ " games, valid = " +validCount);
    }

    public static void writeGameToPGNFile(final File pgnFile,
                                          final MoveLog moveLog) throws IOException {
        final StringBuilder builder = new StringBuilder();
        builder.append(calculateEventString());
        builder.append(calculateDateString() + "\n");
        builder.append(calculatePlyCountString(moveLog) + "\n");
        for(final Move move : moveLog.getMoves()) {
            builder.append(move.toString() + " ");
        }
        try (final Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pgnFile, true)))) {
            writer.write(builder.toString());
        }
    }

    private static String calculateEventString() {
        return "[Event \"" +"Black Widow Game"+ "\"]";
    }

    private static String calculateDateString() {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd");
        return dateFormat.format(new Date());
    }

    private static String calculatePlyCountString(final MoveLog moveLog) {
        return "[PlyCount \"" +moveLog.size() + "\"]";
    }

    public static List<String> processMoveText(final String gameText) throws ParsePGNException {
        return gameText.isEmpty() ? Collections.<String>emptyList() : createMovesFromPGN(gameText);
    }

    private static List<String> createMovesFromPGN(final String pgnText) {
        if(!pgnText.startsWith("1.")) {
            return Collections.emptyList();
        }
        final List<String> sanitizedMoves = new LinkedList<>(Arrays.asList(
                removeParenthesis(pgnText).replaceAll(Pattern.quote("$") + "[0-9]+", "").replaceAll("[0-9]+\\s*\\.\\.\\.", "")
                        .split("\\s*[0-9]+" + Pattern.quote("."))));
        final List<String> processedData = removeEmptyText(sanitizedMoves);
        final String[] moveRows = processedData.toArray(new String[processedData.size()]);
        final ImmutableList.Builder<String> moves = new Builder<>();
        for(final String row : moveRows) {
            final String[] moveContent = removeWhiteSpace(row).split(" ");
            if(moveContent.length == 1) {
                moves.add(moveContent[0]);
            } else if(moveContent.length == 2) {
                moves.add(moveContent[0]);
                moves.add(moveContent[1]);
            } else {
                System.out.println("problem reading: " +pgnText+ " skipping!");
                return Collections.emptyList();
            }
        }
        return moves.build();
    }

    private static List<String> removeEmptyText(final List<String> moves) {
        final List<String> result = new ArrayList<>();
        for(final String moveText : moves) {
            if(!moveText.isEmpty()) {
                result.add(moveText);
            }
        }
        return result;
    }

    private static String removeWhiteSpace(final String row) {
        return row.trim().replaceAll("\\s+", " ");
    }

    private static boolean isTag(final String gameText) {
        return gameText.startsWith("[") && gameText.endsWith("]");
    }

    private static boolean isEndOfGame(final String gameText) {
        return gameText.endsWith("1-0") || gameText.endsWith("0-1") ||
               gameText.endsWith("1/2-1/2") || gameText.endsWith("*");
    }

    private static String removeParenthesis(final String gameText) {
        int parenthesisCounter=0;
        final StringBuilder builder = new StringBuilder();
        for (final char c : gameText.toCharArray()) {
            if (c == '(' || c == '{' ) {
                parenthesisCounter++;
            }
            if (c == ')' || c == '}' ) {
                parenthesisCounter--;
            }
            if (!(c == '(' || c == '{' || c == ')' || c == '}') && parenthesisCounter == 0) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public static Move createMove(final Board board,
                                  final String pgnText) {

        final Matcher kingSideCastleMatcher = KING_SIDE_CASTLE.matcher(pgnText);
        final Matcher queenSideCastleMatcher = QUEEN_SIDE_CASTLE.matcher(pgnText);
        final Matcher plainPawnMatcher = PLAIN_PAWN_MOVE.matcher(pgnText);
        final Matcher attackPawnMatcher = PAWN_ATTACK_MOVE.matcher(pgnText);
        final Matcher pawnPromotionMatcher = PLAIN_PAWN_PROMOTION_MOVE.matcher(pgnText);
        final Matcher attackPawnPromotionMatcher = ATTACK_PAWN_PROMOTION_MOVE.matcher(pgnText);
        final Matcher plainMajorMatcher = PLAIN_MAJOR_MOVE.matcher(pgnText);
        final Matcher attackMajorMatcher = MAJOR_ATTACK_MOVE.matcher(pgnText);

        int currentCoordinate;
        int destinationCoordinate;

        if(kingSideCastleMatcher.matches()) {
            return extractCastleMove(board, "O-O");
        } else if (queenSideCastleMatcher.matches()) {
            return extractCastleMove(board, "O-O-O");
        } else if(plainPawnMatcher.matches()) {
            final String destinationSquare = plainPawnMatcher.group(1);
            destinationCoordinate = BoardUtils.getCoordinateAtPosition(destinationSquare);
            currentCoordinate = deriveCurrentCoordinate(board, "P", destinationSquare, "");
            return Move.MoveFactory.createMove(board, currentCoordinate, destinationCoordinate);
        } else if(attackPawnMatcher.matches()) {
            final String destinationSquare = attackPawnMatcher.group(3);
            destinationCoordinate = BoardUtils.getCoordinateAtPosition(destinationSquare);
            final String disambiguationFile = attackPawnMatcher.group(1) != null ? attackPawnMatcher.group(1) : "";
            currentCoordinate = deriveCurrentCoordinate(board, "P", destinationSquare, disambiguationFile);
            return Move.MoveFactory.createMove(board, currentCoordinate, destinationCoordinate);
        } else if (attackPawnPromotionMatcher.matches()) {
            final String destinationSquare = attackPawnPromotionMatcher.group(2);
            final String disambiguationFile = attackPawnPromotionMatcher.group(1) != null ? attackPawnPromotionMatcher.group(1) : "";
            destinationCoordinate = BoardUtils.getCoordinateAtPosition(destinationSquare);
            currentCoordinate = deriveCurrentCoordinate(board, "P", destinationSquare, disambiguationFile);
            return Move.MoveFactory.createMove(board, currentCoordinate, destinationCoordinate);
        } else if(pawnPromotionMatcher.find()) {
            final String destinationSquare = pawnPromotionMatcher.group(1);
            destinationCoordinate = BoardUtils.getCoordinateAtPosition(destinationSquare);
            currentCoordinate = deriveCurrentCoordinate(board, "P", destinationSquare, "");
            return Move.MoveFactory.createMove(board, currentCoordinate, destinationCoordinate);
        } else if (plainMajorMatcher.find()) {
            final String destinationSquare = plainMajorMatcher.group(3);
            destinationCoordinate = BoardUtils.getCoordinateAtPosition(destinationSquare);
            final String disambiguationFile = plainMajorMatcher.group(2) != null ? plainMajorMatcher.group(2) : "";
            currentCoordinate = deriveCurrentCoordinate(board, plainMajorMatcher.group(1), destinationSquare, disambiguationFile);
            return Move.MoveFactory.createMove(board, currentCoordinate, destinationCoordinate);
        } else if(attackMajorMatcher.find()) {
            final String destinationSquare = attackMajorMatcher.group(4);
            destinationCoordinate = BoardUtils.getCoordinateAtPosition(destinationSquare);
            final String disambiguationFile = attackMajorMatcher.group(2) != null ? attackMajorMatcher.group(2) : "";
            currentCoordinate = deriveCurrentCoordinate(board, attackMajorMatcher.group(1), destinationSquare, disambiguationFile);
            return Move.MoveFactory.createMove(board, currentCoordinate, destinationCoordinate);
        }

        return Move.NULL_MOVE;

    }

    private static Move extractCastleMove(final Board board,
                                          final String castleMove) {
        for (final Move move : board.currentPlayer().getLegalMoves()) {
            if (move.isCastle() && move.toString().equals(castleMove)) {
                return move;
            }
        }
        return Move.NULL_MOVE;
    }

    private static int deriveCurrentCoordinate(final Board board,
                                               final String movedPiece,
                                               final String destinationSquare,
                                               final String disambiguationFile) throws RuntimeException {
        final List<Move> currentCandidates = new ArrayList<>();
        final int destinationCoordinate =  BoardUtils.getCoordinateAtPosition(destinationSquare);
        for (final Move m : board.currentPlayer().getLegalMoves()) {
            if (m.getDestinationCoordinate() == destinationCoordinate && m.getMovedPiece().toString().equals(movedPiece)) {
                currentCandidates.add(m);
            }
        }

        if(currentCandidates.size() == 0) {
            return -1;
        }

        return currentCandidates.size() == 1
                ? currentCandidates.iterator().next().getCurrentCoordinate()
                : extractFurther(currentCandidates, movedPiece, disambiguationFile);

    }

    private static int extractFurther(final List<Move> candidateMoves,
                                      final String movedPiece,
                                      final String disambiguationFile) {

        final List<Move> currentCandidates = new ArrayList<>();

        for(final Move m : candidateMoves) {
            if(m.getMovedPiece().getPieceType().toString().equals(movedPiece)) {
                currentCandidates.add(m);
            }
        }

        if(currentCandidates.size() == 1) {
            return currentCandidates.iterator().next().getCurrentCoordinate();
        }

        final List<Move> candidatesRefined = new ArrayList<>();

        for (final Move m : currentCandidates) {
            final String pos = BoardUtils.getPositionAtCoordinate(m.getCurrentCoordinate());
            if (pos.contains(disambiguationFile)) {
                candidatesRefined.add(m);
            }
        }

        if(candidatesRefined.size() == 1) {
            return candidatesRefined.iterator().next().getCurrentCoordinate();
        }

        return -1;

    }

}
