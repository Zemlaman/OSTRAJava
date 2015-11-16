package cz.cvut.fit.ostrajava.Compiler;

import cz.cvut.fit.ostrajava.Parser.*;
import jdk.nashorn.internal.runtime.regexp.joni.constants.Arguments;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;

/**
 * Created by tomaskohout on 11/12/15.
 */
public class OSTRAJavaCompiler {
    final String THIS_VARIABLE = "this";

    protected SimpleNode node;

    public OSTRAJavaCompiler(ASTCompilationUnit node){
        this.node = node;
    }

    public List<ClassFile> compile() throws CompilerException {
        if (node.jjtGetNumChildren() == 0){
            throw new CompilerException("No classes to compile");
        }

        int i = 0;
        List<ClassFile> classFiles = new ArrayList<>();
        do {
            node = (SimpleNode)node.jjtGetChild(i);
            if (node instanceof ASTClass){
                classFiles.add(compileClass((ASTClass)node));
            }

            i++;
        }while(i < node.jjtGetNumChildren());

        return classFiles;
    }


    protected ClassFile compileClass(ASTClass node) throws CompilerException {

        ClassFile classFile = new ClassFile(node.getName(), node.getExtending());
        List<Field> fields = new ArrayList<Field>();

        for (int i=0; i<node.jjtGetNumChildren(); i++){
            Node child = node.jjtGetChild(i);

            if (child instanceof ASTFieldDeclaration){
                fields.addAll(fieldDeclaration((ASTFieldDeclaration)child));
            }else if (child instanceof ASTMethodDeclaration){
                Method method = methodDeclaration((ASTMethodDeclaration)child, node.getName());
                classFile.addMethod(method);
            }
        }

        classFile.setFields(fields);
        return classFile;
    }

    protected List<Field> fieldDeclaration(ASTFieldDeclaration node) throws CompilerException {
        Type type;

        //First child is Type
        type = type((ASTType)node.jjtGetChild(0));
        List<Field> fields = new ArrayList<Field>();

        //Second and others (There can be more fields declared) are names
        for (int i=1; i<node.jjtGetNumChildren(); i++) {
            ASTVariable nameNode = (ASTVariable) node.jjtGetChild(i);

            Field field = new Field(nameNode.jjtGetValue().toString(), type);
            fields.add(field);
        }

        return fields;
    }

    protected Type type(ASTType node) throws CompilerException {
        Type type;

        Node typeNode = node.jjtGetChild(0);

        if (typeNode instanceof ASTBool){
            type = Type.Boolean();
        }else if (typeNode instanceof  ASTNumber){
            type = Type.Number();
        }else if (typeNode instanceof  ASTString){
            type = Type.String();
        }else if (typeNode instanceof  ASTName){
            String className = (String)((ASTName) typeNode).jjtGetValue();
            type = Type.Reference(className);
        }else{
            throw new CompilerException("Unexpected type of field " + typeNode );
        }

        return type;
    }

    protected Method methodDeclaration(ASTMethodDeclaration node, String className) throws CompilerException {
        Type returnType = Type.Void();
        String name = null;
        List<Type> args = new ArrayList<>();
        ByteCode byteCode = new ByteCode();
        for (int i=0; i<node.jjtGetNumChildren(); i++) {
            Node child = node.jjtGetChild(i);

            if (child instanceof ASTResultType){
                ASTResultType resultType = ((ASTResultType) child);
                if (resultType.jjtGetNumChildren() != 0){
                    returnType = type((ASTType)resultType.jjtGetChild(0));
                }
            }else if (child instanceof ASTMethod){


                name = ((ASTMethod) child).jjtGetValue().toString();

                //Add This as first argument
                byteCode.addLocalVariable(THIS_VARIABLE, Type.Reference(className));

                //Add the rest of arguments
                ASTFormalParameters params = (ASTFormalParameters)child.jjtGetChild(0);
                args = formalParameters(params, byteCode);
            }else if (child instanceof ASTBlock){
                methodBlock((ASTBlock) child, byteCode);
            }
        }

        if (name == null){
            throw new CompilerException("Missing method name in " + node );
        }

        Method method = new Method(name, args, returnType);
        method.setByteCode(byteCode);

        return method;
    }

    protected List<Type> formalParameters(ASTFormalParameters node, ByteCode byteCode) throws CompilerException {
        List<Type> args = new ArrayList<>();

        for (int i=0; i<node.jjtGetNumChildren(); i++) {
            ASTFormalParameter param = (ASTFormalParameter)node.jjtGetChild(i);
            Type type = type((ASTType) param.jjtGetChild(0));

            String name = ((ASTVariable) param.jjtGetChild(1)).jjtGetValue().toString();

            args.add(type);
            byteCode.addLocalVariable(name, type);
        }

        return args;
    }

    protected ByteCode methodBlock(ASTBlock node, ByteCode byteCode) throws CompilerException {

        //TODO: So far it's the same, in future it will probably need to load arguments etc...
        return block(node, byteCode);
    }

    protected ByteCode block(ASTBlock node, ByteCode byteCode) throws CompilerException {
        List<String> args = null;

        for (int i=0; i<node.jjtGetNumChildren(); i++) {
            Node child = node.jjtGetChild(i);

            if (child instanceof ASTLocalVariableDeclaration){
                byteCode = localVariableDeclaration((ASTLocalVariableDeclaration) child, byteCode);
            }else if (child instanceof ASTStatement){
                statement((ASTStatement) child, byteCode);
            }
        }

        return byteCode;
    }

    protected ByteCode statement(ASTStatement node, ByteCode byteCode) throws CompilerException {

        for (int i=0; i<node.jjtGetNumChildren(); i++) {
            Node child = node.jjtGetChild(i);

            //Everything written with a 'pyco' in the end
            if (child instanceof ASTStatementExpression){
                Node statementExpression = ((ASTStatementExpression)child);
                //It's an assignment
                if (statementExpression.jjtGetChild(0) instanceof ASTAssignment){
                    assignment((ASTAssignment) statementExpression.jjtGetChild(0), byteCode);
                //It's a call
                }else if (statementExpression.jjtGetChild(0) instanceof ASTPrimaryExpression){
                    call(statementExpression.jjtGetChild(0), byteCode);
                }

            }else if (child instanceof ASTBlock){


            }else if (child instanceof ASTIfStatement){
                byteCode = ifStatement((ASTIfStatement)child, byteCode);
            }else if (child instanceof ASTPrintStatement){


            }else if (child instanceof ASTReturnStatement){


            }
            /*if (child instanceof ASTLocalVariableDeclaration){
                byteCode = localVariableDeclaration((ASTLocalVariableDeclaration) child, byteCode);
            }else if (child instanceof ASTStatement){
                statement((ASTStatement) child);
            }*/
        }

        return byteCode;
    }

    protected ByteCode assignment(ASTAssignment node, ByteCode byteCode) throws CompilerException {
        //Assignee -> AssignmentPrefix -> Left
        Node left = node.jjtGetChild(0).jjtGetChild(0).jjtGetChild(0);

        //We have to go recursively to the bottom of the tree to find the real expression
        Node right = simplifyExpression(node.jjtGetChild(1));

        //We are assigning to a variable
        if (isVariable(left)) {

            String name = ((ASTName) left).jjtGetValue().toString();
            int position = byteCode.getPositionOfLocalVariable(name);

            if (position == -1){
                throw new CompilerException("Trying to assign to an undeclared variable '" + name + "'");
            }

            Type type = byteCode.getTypeOfLocalVariable(name);

            //If the expression is a condition we have to evaluate it
            if (isConditionalExpression(right)){
                if (type != Type.Boolean()){
                    throw new CompilerException("Trying to assign boolean expression to variable '" + name + "' of type " + type);
                }

                //Creates bytecode for the expression
                List<Instruction> ifInstructions = expression(right, byteCode);

                evaluateIfExpression(right, byteCode.getLastInstruction());

                //If we success set variable to TRUE
                byteCode.addInstruction(new Instruction(InstructionSet.PushInteger, "1"));
                byteCode.addInstruction(new Instruction(InstructionSet.GoTo, Integer.toString(byteCode.getLastInstructionPosition()+2)));
                //Else set it to false
                byteCode.addInstruction(new Instruction(InstructionSet.PushInteger, "0"));

                //Set end block instructions to go to false
                for (Instruction instr: ifInstructions) {
                    instr.setOperand(0, Integer.toString(byteCode.getLastInstructionPosition()));
                }
            }else if (isNumberLiteral(right) || isAdditiveExpression(right) || isMultiplicativeExpression(right)){
                if (type != Type.Number()){
                    throw new CompilerException("Trying to assign Number to a variable '" + name + "' of type " + type);
                }

                expression(right, byteCode);
                byteCode.addInstruction(new Instruction(InstructionSet.StoreInteger, Integer.toString(position)));

            }else if (isBooleanLiteral(right)) {
                if (type != Type.Boolean()) {
                    throw new CompilerException("Trying to assign Boolean to a variable '" + name + "' of type " + type);
                }

                expression(right, byteCode);
                byteCode.addInstruction(new Instruction(InstructionSet.StoreInteger, Integer.toString(position)));
            }else if (isVariable(right)){
                String rightName = ((ASTName)right).jjtGetValue().toString();

                int rightPosition = byteCode.getPositionOfLocalVariable(name);

                if (rightPosition == -1){
                    throw new CompilerException("Variable '" + rightName + "' is undeclared");
                }

                Type rightType = byteCode.getTypeOfLocalVariable(rightName);

                if (rightType != type){
                    throw new CompilerException("Trying to assign variable '" + rightName +  "' of type " + rightType + " to a variable '" + name + "' of type " + type);
                }

                expression(right, byteCode);

                if (type.isReference()) {
                    byteCode.addInstruction(new Instruction(InstructionSet.StoreReference, Integer.toString(position)));
                }else{
                    byteCode.addInstruction(new Instruction(InstructionSet.StoreInteger, Integer.toString(position)));
                }
            }else if (isAllocationExpression(right)){
                if (!type.isReference()) {
                    throw new CompilerException("Trying to assign Object to a variable '" + name + "' of type " + type);
                }

                expression(right, byteCode);
                byteCode.addInstruction(new Instruction(InstructionSet.StoreReference, Integer.toString(position)));
            }else{
                throw new NotImplementedException();
            }

        }else{
            throw new NotImplementedException();
        }


        return byteCode;
    }

    protected void call(Node node, ByteCode bytecode) throws CompilerException{
        if (node.jjtGetNumChildren() <= 1){
            throw new CompilerException("Expected function call");
        }

        //Can either be object / object field / method name
        List<Node> objects = new ArrayList<>();

        //List of methods to call
        Map<String, List<Node>> methods = new LinkedHashMap<>();


        //PrimaryPrefix -> This / Super / Name
        Node prefix = (Node) node.jjtGetChild(0).jjtGetChild(0);

        //It is simple method call
        if (node.jjtGetNumChildren() == 2){
            //Add method name
            objects.add(prefix);

            //Set prefix as This
            prefix = new ASTThis(prefix.getId());
        }

        if (prefix instanceof ASTName) {
            expression(prefix, bytecode);
        }else if (prefix instanceof ASTThis){
            loadThis(bytecode);
        }else{
            throw new NotImplementedException();
        }


        for (int i=1; i<node.jjtGetNumChildren(); i++) {
            Node suffix = (ASTPrimarySuffix) node.jjtGetChild(i);
            Node child = suffix.jjtGetChild(0);

            //it's arguments
            if (child instanceof ASTArguments){

                if (objects.size() == 0){
                    throw new CompilerException("Unexpected argument list");
                }

                //Get last object (which is actually method name) and remove from the list
                Node method = objects.get(objects.size() - 1);
                objects.remove(objects.size() - 1);

                String methodName = "";

                if (method instanceof ASTName) {
                    methodName = ((ASTName) method).jjtGetValue().toString();
                }else{
                    throw new CompilerException("Unexpected call of this() or super() in non-constructor method");
                }

                //Suffix -> Arguments
                arguments(child, bytecode);

                bytecode.addInstruction(new Instruction(InstructionSet.InvokeVirtual, methodName));
            //it's object or object in field
            }else if (child instanceof ASTName){
                objects.add(child);
            }
        }

        //If there are fields on the object
        //TODO: Make it work with fields e.g.: this.foo.goo.boo()
        if (objects.size() > 1){
            throw new NotImplementedException();
        }


        return;
    }

    protected void loadThis(ByteCode byteCode){
        byteCode.getPositionOfLocalVariable(THIS_VARIABLE);
        //This is always stored as first reference
        byteCode.addInstruction(new Instruction(InstructionSet.LoadReference, "0"));
    }

    protected void loadSuper(ByteCode byteCode){
        //TODO: load super
    }

    protected void arguments(Node node, ByteCode byteCode) throws CompilerException {
        for (int i=0; i<node.jjtGetNumChildren(); i++) {
            Node child = node.jjtGetChild(i);
            child = simplifyExpression(child);

            expression(child, byteCode);

            //TODO: evaluate bool expressions
        }
    }



    protected ByteCode ifStatement(ASTIfStatement node, ByteCode byteCode) throws CompilerException {

        List<Instruction> gotoInstructions = new ArrayList<>();

        //The conditions
        for (int i=0; i<node.jjtGetNumChildren(); i+=2) {
            //Else Statement
            if (i == node.jjtGetNumChildren()-1){
                ASTBlock block = (ASTBlock) node.jjtGetChild(i);
                block(block, byteCode);
            //If or else-if statement
            }else{
                boolean b = 1 > 2;
                Node child = simplifyExpression(node.jjtGetChild(i));

                //If-expression skip block instructions
                List<Instruction> endBlockInstructions = ifExpression(child, byteCode);
                evaluateIfExpression(child, byteCode.getLastInstruction());


                ASTBlock block = (ASTBlock) node.jjtGetChild(i+1);
                block(block, byteCode);

                //This creates goto instruction on the end of the block which leads to the end of branching
                Instruction gotoInstruction = byteCode.addInstruction(new Instruction(InstructionSet.GoTo, "?"));
                gotoInstructions.add(gotoInstruction);

                //Change the compare instr. so it points to the end of the block
                for (Instruction ebi: endBlockInstructions) {
                    ebi.setOperand(0, Integer.toString(byteCode.getLastInstructionPosition() + 1));
                }
            }

        }

        //Go through all goto instruction and set them to the end
        for (Instruction i : gotoInstructions){
            i.setOperand(0, Integer.toString(byteCode.getLastInstructionPosition() + 1));
        }
        return byteCode;
    }

    protected void evaluateIfExpression(Node node, Instruction lastInstruction) throws CompilerException {
        //When it's single if expression we have to invert last member
        if (isRelationalExpression(node) || isEqualityExpression(node)){
            lastInstruction.invert();
        }
    }

    //Traverse through the expression tree and simplify it so that the expression children are the immediate children
    protected Node simplifyExpression(Node node) throws  CompilerException{
        if (node.jjtGetNumChildren() == 1){
            return simplifyExpression(node.jjtGetChild(0));
        }else if (node.jjtGetNumChildren() > 1){
            //Go recursively through the children
            for (int i=0; i<node.jjtGetNumChildren(); i++) {
                Node child = simplifyExpression(node.jjtGetChild(i));
                //Replace the old expression
                node.jjtAddChild(child, i);
            }
        }
        //Return node if there are no more children
        return node;
    }

    protected List<Instruction> expression(Node node, ByteCode byteCode) throws CompilerException{
        if (node.jjtGetNumChildren() > 1) {
            if (isConditionalExpression(node)) {
                return ifExpression(node, byteCode);
            }else if (isArithmeticExpression(node)) {
                arithmeticExpression(node, byteCode);
            }else if (isAllocationExpression(node)) {
                allocationExpression((ASTAllocationExpression)node, byteCode);
            }
        }else if (isVariable(node)){
            String name = ((ASTName) node).jjtGetValue().toString();
            int position = byteCode.getPositionOfLocalVariable(name);
            Type type = byteCode.getTypeOfLocalVariable(name);

            if (position == -1){
                throw new CompilerException("Variable '" + name + "' is not declared");
            }

            if (type == Type.Number() || type == Type.Boolean()) {
                byteCode.addInstruction(new Instruction(InstructionSet.LoadInteger, Integer.toString(position)));
            }else if (type.isReference()){
                byteCode.addInstruction(new Instruction(InstructionSet.LoadReference, Integer.toString(position)));
            }else{
                throw new NotImplementedException();
            }
        }else if (isNumberLiteral(node)){
            String value = ((ASTNumberLiteral) node).jjtGetValue().toString();
            byteCode.addInstruction(new Instruction(InstructionSet.PushInteger, value));
        }else{
            throw new NotImplementedException();
        }

        return null;
    }

    protected void allocationExpression(ASTAllocationExpression node, ByteCode byteCode) throws CompilerException {
        String name = ((ASTName)node.jjtGetChild(0)).jjtGetValue().toString();

        //TODO: add invocation of constructor

        byteCode.addInstruction(new Instruction(InstructionSet.New, name));
    }

    protected List<Instruction> ifExpression(Node node, ByteCode byteCode) throws CompilerException {
        if (isEqualityExpression(node)|| isRelationalExpression(node)){
            return compareExpression(node, byteCode);
        }else if (isOrExpression(node)) {
            return orExpression((ASTConditionalOrExpression)node, byteCode);
        }else if (isAndExpression(node)){
            return andExpression((ASTConditionalAndExpression)node, byteCode);
        }else{
            throw new NotImplementedException();
        }
    }

    protected List<Instruction> compareExpression(Node node, ByteCode byteCode) throws CompilerException {
        List<Instruction> instructions = new ArrayList<>();

        Node first = node.jjtGetChild(0);
        expression(first, byteCode);

        for (int i = 1; i < node.jjtGetNumChildren(); i += 2) {
            Node operator = node.jjtGetChild(i);
            Node child = node.jjtGetChild(i + 1);

            expression(child, byteCode);

            Instruction instruction = null;

            if (isEqualityExpression(node)) {
                if (operator instanceof ASTEqualOperator) {
                    instruction = new Instruction(InstructionSet.IfCompareEqualInteger, "?");
                } else {
                    instruction = new Instruction(InstructionSet.IfCompareNotEqualInteger, "?");
                }
            } else if (isRelationalExpression(node)) {

                if (operator instanceof ASTGreaterThanOperator) {
                    instruction = new Instruction(InstructionSet.IfCompareGreaterThanInteger, "?");
                } else if (operator instanceof ASTGreaterThanOrEqualOperator) {
                    instruction = new Instruction(InstructionSet.IfCompareGreaterThanOrEqualInteger, "?");
                } else if (operator instanceof ASTLessThanOperator) {
                    instruction = new Instruction(InstructionSet.IfCompareLessThanInteger, "?");
                } else if (operator instanceof ASTLessThanOrEqualOperator) {
                    instruction = new Instruction(InstructionSet.IfCompareLessThanOrEqualInteger, "?");
                }
            }

            if (instruction == null){
                throw new NotImplementedException();
            }

            instructions.add(instruction);
            byteCode.addInstruction(instruction);
        }

        return instructions;
    }

    protected List<Node> mergeConditionals(Node node) {
        List<Node> merged = new ArrayList<>();

        for (int i = 0; i < node.jjtGetNumChildren(); i += 1) {
            Node child = node.jjtGetChild(i );

            if ((isOrExpression(node) && isOrExpression(child)) || (isAndExpression(node) && isAndExpression(child) ) ){
                merged.addAll(mergeConditionals(child));
            }else{
                merged.add(child);
            }
        }

        return merged;
    }

    protected List<Instruction> orExpression(ASTConditionalOrExpression node, ByteCode byteCode) throws CompilerException {

        //Instructions that should go to execution block if passed
        List<Instruction> toBlockInstructions = new ArrayList<>();

        //Indicates whether last child is an nested AND
        boolean lastChildAnd = false;

        //Instruction that will skip the execution block if passed
        List<Instruction> endBlockInstruction = new ArrayList<>();

        //Merge together same conditionals for easier computation (e.g. ( x or ( y or z) )
        List<Node> children = mergeConditionals(node);

        for (int i = 0; i < children.size(); i += 2) {
            Node child = children.get(i);

            List<Instruction> childInstructions = ifExpression(child, byteCode);

                //In nested AND expression
                if (isAndExpression(child)) {

                    //It's the last, every instruction leads to the end
                    if (i == node.jjtGetNumChildren() - 1) {
                        lastChildAnd = true;
                        endBlockInstruction.addAll(childInstructions);

                    //It's not the last, every instruction goes to next condition. Last instruction goes to block if passed
                    } else {
                        Iterator<Instruction> itr = childInstructions.iterator();

                        while (itr.hasNext()) {
                            Instruction instruction = itr.next();

                            if (itr.hasNext()) {
                                instruction.setOperand(0, Integer.toString(byteCode.getLastInstructionPosition() + 1));
                            } else {
                                toBlockInstructions.add(instruction);
                                instruction.invert();
                            }
                        }
                    }

                //It's simple condition
                } else {
                    toBlockInstructions.addAll(childInstructions);
                }
        }

        Iterator<Instruction> itr = toBlockInstructions.iterator();

        while(itr.hasNext()) {
            Instruction instruction = itr.next();

            //Not last or the AND is the last
            if (itr.hasNext() || lastChildAnd) {
                //Go to the code
                instruction.setOperand(0, Integer.toString(byteCode.getLastInstructionPosition() + 1));
            } else {
                //Invert last instruction and send it to the end
                instruction.invert();
                endBlockInstruction.add(instruction);
            }

        }

        return endBlockInstruction;
    }

    protected List<Instruction> andExpression(ASTConditionalAndExpression node, ByteCode byteCode) throws CompilerException {
        List<Instruction> instructions = new ArrayList<>();

        //Instruction that will skip the execution block if passed
        List<Instruction> endBlockInstruction = new ArrayList<>();

        //Merge together same conditionals for easier computation (e.g. ( x and ( y and z) )
        List<Node> children = mergeConditionals(node);

        for (int i = 0; i < children.size(); i += 2) {
            Node child = children.get(i);
            List<Instruction> childInstructions = ifExpression(child, byteCode);

                //In nested AND expression
                if (isOrExpression(child)){
                    endBlockInstruction.addAll(childInstructions);
                } else {
                    instructions.addAll(childInstructions);
                }

        }

        Iterator<Instruction> itr = instructions.iterator();

        while(itr.hasNext()) {
            Instruction instruction = itr.next();

            //Invert instruction and send it to end block
            instruction.invert();
            endBlockInstruction.add(instruction);
        }

        return endBlockInstruction;
    }

    protected void arithmeticExpression(Node node, ByteCode byteCode) throws CompilerException {
        Node first = node.jjtGetChild(0);
        expression(first, byteCode);

        for (int i = 1; i < node.jjtGetNumChildren(); i += 2) {
            Node operator = node.jjtGetChild(i);
            Node child = node.jjtGetChild(i + 1);

            expression(child, byteCode);

            Instruction instruction = null;

            if (isAdditiveExpression(node)) {
                if (operator instanceof ASTPlusOperator) {
                    instruction = new Instruction(InstructionSet.AddInteger);
                } else {
                    instruction = new Instruction(InstructionSet.SubstractInteger);
                }
            } else if (isMultiplicativeExpression(node)) {
                if (operator instanceof ASTMultiplyOperator) {
                    instruction = new Instruction(InstructionSet.MultiplyInteger);
                } else if (operator instanceof ASTDivideOperator) {
                    instruction = new Instruction(InstructionSet.DivideInteger);
                } else if (operator instanceof ASTModuloOperator) {
                    instruction = new Instruction(InstructionSet.ModuloInteger);
                }
            }

            if (instruction == null) {
                throw new NotImplementedException();
            }

            byteCode.addInstruction(instruction);
        }

    }


    protected ByteCode localVariableDeclaration(ASTLocalVariableDeclaration node, ByteCode byteCode) throws CompilerException {


        //First child is Type
        Type type = type((ASTType) node.jjtGetChild(0));

        //Second and others (There can be more fields declared) are names
        for (int i=1; i<node.jjtGetNumChildren(); i++) {
            ASTVariableDeclarator declarator = (ASTVariableDeclarator) node.jjtGetChild(i);
            String name = ((ASTVariable) declarator.jjtGetChild(0)).jjtGetValue().toString();

            String valueString = null;
            SimpleNode value = null;

            //We also assigned value
            if (declarator.jjtGetNumChildren() > 1) {
                value = (SimpleNode) declarator.jjtGetChild(1);
                valueString = value.jjtGetValue().toString();
            }

            int position = byteCode.addLocalVariable(name, type);

            if (position == -1) {
                throw new CompilerException("Variable '" + name + "' has been already declared");
            }

            if (type == Type.Number()) {
                if (value != null) {
                    if (!(isNumberLiteral(value))) {
                        throw new CompilerException("Assigning Non-Number literal to a Number type variable");
                    }
                } else {
                    //Default value for integer
                    valueString = "0";
                }

                byteCode.addInstruction(new Instruction(InstructionSet.PushInteger, valueString));
                byteCode.addInstruction(new Instruction(InstructionSet.StoreInteger, Integer.toString(position)));
            }else if (type == Type.Boolean()){
                if (value != null){
                    if (!(isBooleanLiteral(value))){
                        throw new CompilerException("Assigning Non-Bool literal to a Bool type variable");
                    }
                }else{
                    //Default value for boolean
                    valueString = "0";
                }

                byteCode.addInstruction(new Instruction(InstructionSet.PushInteger, valueString));
                byteCode.addInstruction(new Instruction(InstructionSet.StoreInteger, Integer.toString(position)));

            //It's class
            }else if (type.isReference()){
                if (value != null){
                    throw new CompilerException("Assigning Literal to a Object type variable");
                }else{
                    //No default value for objects?
                    //TODO: maybe put null there?
                }
            }

        }

        return byteCode;
    }

    boolean isConditionalExpression(Node node){
        return isEqualityExpression(node) || isRelationalExpression(node) || isOrExpression(node) || isAndExpression(node);
    }

    boolean isOrExpression(Node node){
        return node instanceof ASTConditionalOrExpression;
    }

    boolean isAndExpression(Node node){
        return node instanceof ASTConditionalAndExpression;
    }

    boolean isEqualityExpression(Node node){
        return node instanceof ASTEqualityExpression;
    }

    boolean isRelationalExpression(Node node){
        return node instanceof ASTRelationalExpression;
    }

    boolean isArithmeticExpression(Node node){
        return isAdditiveExpression(node) || isMultiplicativeExpression(node);
    }

    boolean isAdditiveExpression(Node node){
        return node instanceof ASTAdditiveExpression;
    }
    boolean isMultiplicativeExpression(Node node){
        return node instanceof ASTMultiplicativeExpression;
    }

    boolean isVariable(Node node){
        return node instanceof ASTName;
    }

    boolean isLiteral(Node node){
        return isNumberLiteral(node) || isBooleanLiteral(node);
    }

    boolean isNumberLiteral(Node node){
        return node instanceof ASTNumberLiteral;
    }

    boolean isBooleanLiteral(Node node){
        return node instanceof ASTBooleanLiteral;
    }

    boolean isAllocationExpression(Node node){
        return node instanceof ASTAllocationExpression;
    }
}