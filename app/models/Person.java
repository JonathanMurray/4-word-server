package models;

import java.io.Serializable;

/**
 * Created by jonathan on 2015-07-06.
 */
public class Person implements Serializable {
    public static final long serialVersionUID = 0;
    public final String name;
    public final int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String toString(){
        return "[" + name + ", " + age + "]";
    }
}
