package dev.macepvpmod;
final class SoundPlaylistCursor {
    private int next;
    int next(int size) { if(size<=0){reset();return -1;}int index=next%size;next=(index+1)%size;return index; }
    void reset(){next=0;}
}
