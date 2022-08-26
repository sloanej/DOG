package org.projectdog;

public class Button {

    private String name;
    private int image;

    public Button(){

    }

    public Button(String name, int image){
        this.name = name;
        this.image=image;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public Integer getImage(){
        return image;
    }

    public void setImage(int image){
        this.image = image;
    }

}
