/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("HotFix")

import org.objectweb.asm.*
import java.io.*

private const val CLASS_EXT = ".class"
private const val CLINIT = "<clinit>"
private const val CLINIT_DESC = "()V"
private const val PROCESSED_ANN = "HotFixProcessed"

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: HotFix <classes-directory> <main-class-name> [-q]")
        return
    }
    val classesDirectory = File(args[0])
    val mainClass = args[1].replace('.', '/') // converted to binary name just in case
    val quiet = args.size >= 3 && args[2] == "-q"
    fun log(message: String) {
        if (!quiet) println("HotFix: $message")
    }
    fun String.toFile() = File(classesDirectory, this.replace('/', File.separatorChar) + CLASS_EXT)
    val cs = ClassScanner(String::toFile)
    cs.enqueue(mainClass)
    while (!cs.isEmpty()) {
        val name = cs.removeFirst()
        log("Scanning $name")
        cs.scan(name.toFile())
    }
    cs.external.forEach { log("Found reference: $it") }
    log("Patching: $mainClass")
    ClassPatcher(cs.external).patch(mainClass.toFile())


}

class ClassScanner(private val toFile: String.() -> File) {
    val external = HashSet<String>()
    private val inQueue = HashSet<String>()
    private val queue = ArrayDeque<String>()

    fun enqueue(name: String) {
        if (!inQueue.add(name)) return
        if (name.toFile().exists()) {
            queue += name
        } else {
            external += name
        }
    }

    fun isEmpty() = queue.isEmpty()
    fun removeFirst() = queue.removeFirst()

    private val classVisitor = ClassVisitorImpl()
    private val methodVisitor = MethodVisitorImpl()

    fun scan(file: File) {
        file.inputStream().use { inStream ->
            ClassReader(inStream).accept(classVisitor, ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES)
        }
    }

    private fun enqueue(type: Type) {
        when (type.sort) {
            Type.OBJECT -> enqueue(type.internalName)
            Type.ARRAY -> enqueue(type.elementType)
        }
    }

    private inner class ClassVisitorImpl : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor = methodVisitor
    }

    private inner class MethodVisitorImpl : MethodVisitor(Opcodes.ASM9) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String?,
            descriptor: String?,
            isInterface: Boolean
        ) {
            if (opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKESPECIAL) {
                enqueue(owner)
            }
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
            when (opcode) {
                Opcodes.NEW -> enqueue(type)
                Opcodes.ANEWARRAY -> enqueue(Type.getObjectType(type))
            }
        }

        override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
            enqueue(Type.getType(descriptor))
        }

        override fun visitLdcInsn(value: Any?) {
            when (value) {
                is Type -> enqueue(value)
                is Handle -> enqueue(value.owner)
            }
        }
    }
}

class ClassPatcher(private val external: Set<String>) {
    fun patch(file: File) {
        val bytes = file.inputStream().use { inStream ->
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
            ClassReader(inStream).accept(ClassVisitorImpl(cw), 0)
            cw.toByteArray()
        }
        file.writeBytes(bytes)
    }

    private inner class ClassVisitorImpl(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM9, cv) {
        private var clinitSeen = false
        private var wasProcessed = false

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
            if (descriptor == PROCESSED_ANN) {
                wasProcessed = true
            }
            return super.visitAnnotation(descriptor, visible)
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor =
            if (name == CLINIT && descriptor == CLINIT_DESC && !wasProcessed) {
                clinitSeen = true
                visitAnnotation(PROCESSED_ANN, false)
                ClinitMethodVisitorImpl(super.visitMethod(access, name, descriptor, signature, exceptions))
            } else {
                super.visitMethod(access, name, descriptor, signature, exceptions)
            }

        override fun visitEnd() {
            if (!clinitSeen && !wasProcessed) {
                visitMethod(Opcodes.ACC_STATIC, CLINIT, CLINIT_DESC, null, null).apply {
                    visitCode()
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(1, 0)
                    visitEnd()
                }
            }
            super.visitEnd()
        }
    }

    private inner class ClinitMethodVisitorImpl(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitCode() {
            super.visitCode()
            external.forEach { ex ->
                visitLdcInsn(Type.getObjectType(ex))
                visitInsn(Opcodes.POP)
            }
        }
    }
}