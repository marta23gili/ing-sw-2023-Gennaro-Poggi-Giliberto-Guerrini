package Server.Model.Cards.CommonGoalCards;

import Server.Model.Cards.CommonGoalCard;
import Server.Model.GameItems.Bookshelf;
import Server.Model.GameItems.BookshelfTileSpot;
import Server.Model.GameItems.TileType;

/**
 * This class represents the second common goal card
 * @author due2
 */

public class CommonGoalCard2 extends CommonGoalCard {

    /**
     * constructor of the class CommonGoalCard2 that calls the constructor of the superclass
     * @param playersNum the number of players in the game
     * @param secondIstance true if it is the second card, false otherwise in order to know if the card has to be created with the second instance of the points tiles
     */
    public CommonGoalCard2(int playersNum, boolean secondIstance) {
        super(playersNum, secondIstance);
    }


    /**
     * This method checks if the common goal card is completed
     * @param bookshelf the bookshelf of the player
     * @return true if the common goal card is completed, false otherwise
     */
    @Override
    public boolean check(Bookshelf bookshelf) {
        BookshelfTileSpot[][] shelf = bookshelf.getTileMatrix();
        int count = 0;
        boolean flag;
        int[] verifier = new int[6];

        //verifies if there are at least two rows with one tile of each type

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 6; j++) {
                if (shelf[j][i].getTileType() == TileType.PLANTS) {
                    verifier[0] += 1;
                } else if (shelf[j][i].getTileType() == TileType.FRAMES) {
                    verifier[1] += 1;
                } else if (shelf[j][i].getTileType() == TileType.BOOKS) {
                    verifier[2] += 1;
                } else if (shelf[j][i].getTileType() == TileType.CATS) {
                    verifier[3] += 1;
                } else if (shelf[j][i].getTileType() == TileType.GAMES) {
                    verifier[4] += 1;
                } else if (shelf[j][i].getTileType() == TileType.TROPHIES) {
                    verifier[5] += 1;
                }
            }
            flag = true;
            for (int k = 0; k < 6; k++) {
                if (verifier[k] != 1) {
                    flag = false;
                }
            }
            if (flag) {
                count++;
            }
            for (int k = 0; k < 6; k++) {
                verifier[k] = 0;
            }
        }
        if (count >=2) {
            return true;
        } else {
            return false;
        }
    }

    public char[][] getCLIRepresentation(){
        char[][] res  = new char[13][15];
        res[0][0] = '|';
        res[0][14] = '|';
        res[12][0] = '|';
        res[12][14] = '|';
        for(int i=0;i<13;i++){
            res[i][1] = ' ';
            res[i][13] = ' ';
        }
        for(int i=0;i<13;i++){
            for(int j=2;j<13;j++){
                if(i%2 == 0){
                    if(j%2 == 0){
                        res[i][j] = '+';
                    }else{
                        res[i][j] = '-';
                    }
                }else{
                    if(j%2 == 0){
                        res[i][j] = '|';
                    }else{
                        res[i][j] = ' ';
                    }
                }
            }
        }
        res[1][3] = '=';
        res[3][3] = '=';
        res[5][3] = '=';
        res[7][3] = '=';
        res[9][3] = '=';
        res[11][3] = '=';

        res[1][9] = '=';
        res[3][9] = '=';
        res[5][9] = '=';
        res[7][9] = '=';
        res[9][9] = '=';
        res[11][9] = '=';

        return res;
    }

    public String[] getCommonGoalDescription(){
        String[] description = new String[8];

        description[0] = "                           ";
        description[1] = "                           ";
        description[2] = "                           ";
        description[3] = "Two columns each formed by ";
        description[4] = "6 different types of tiles.";
        description[5] = "                           ";
        description[6] = "                           ";
        description[7] = "                           ";

        return description;
    }

    /**
     * This method returns the ID of the common goal card
     * @return the ID of the common goal card
     */
    @Override
    public int getCardID() {
        return 2;
    }


}
