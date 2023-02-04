package edu.avispa;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

public class Class {
    public static ConcurrentLinkedQueue<Class> equivalentClasses;
    public ConcurrentSkipListSet<String> equivalent;
    public ConcurrentSkipListSet<String> notEquivalent;

    private Class(String initialElement){
        equivalent = new ConcurrentSkipListSet<>();
        equivalent.add(initialElement);
        notEquivalent = new ConcurrentSkipListSet<>();
    }

    public static void init(){
        equivalentClasses = new ConcurrentLinkedQueue<>();
    }

    public static synchronized Class getClassOf(String e){
        for (Class eClass : Class.equivalentClasses) {
            if(eClass.equivalent.contains(e)) return eClass;
        }
        Class newClass = new Class(e);
        equivalentClasses.add(newClass);
        return newClass;
    }

}
