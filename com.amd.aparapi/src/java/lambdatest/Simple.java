package lambdatest;

import com.amd.aparapi.Aparapi;

import javax.lang.model.util.SimpleElementVisitor6;


public class Simple {

    void go(){
        int[] in = new int[1024];
        int[] out = new int[1024];
        for(int i=0; i<1024; i++){
            in[0]=i;
            out[0]=0;
        }
        Aparapi.forEach(100, (i) ->{out[i]=in[i]*2;});
    }
    public static void main(String[] args){
       new Simple().go();

    }
}
