package caliniya.vergvoke.game;

import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.game.data.*;

public class Teams {
    private static TeamData[] datas;

    public static void init() {
        TeamTypes[] allTeams = TeamTypes.values();
        datas = new TeamData[allTeams.length];

        for (int i = 0; i < allTeams.length; i++) {
            datas[i] = new TeamData(allTeams[i]);
        }
    }

    public static TeamData get(TeamTypes team) {
        if (datas == null || team == null)
            return null;
        return datas[team.ordinal()];
    }
}